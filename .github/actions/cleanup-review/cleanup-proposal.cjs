// Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0
'use strict';
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const { execFileSync } = require('node:child_process');

function requireSha(sha, label) {
  if (typeof sha !== 'string' || !/^(?:[a-f0-9]{40}|[a-f0-9]{64})$/.test(sha)) throw new Error(`Invalid ${label} SHA`);
}
function utf8(bytes) {
  const text = bytes.toString('utf8');
  if (!Buffer.from(text).equals(bytes)) throw new Error('Invalid UTF-8 bytes');
  return text;
}
function requirePath(file) {
  if (typeof file !== 'string' || !file.endsWith('.java') || file.includes('\0')
      || file.split('/').some((part) => ['', '.', '..'].includes(part)) || Buffer.from(file).toString('utf8') !== file) {
    throw new Error('Proposal changes require valid UTF-8 Java paths');
  }
}
function requireMode(mode) {
  if (!['100644', '100755', '120000'].includes(mode)) throw new Error(`Unsupported Git mode ${mode}; gitlinks are not cleanup blobs`);
}

function captureProposal({ headSha, patch, cwd = process.cwd() }) {
  requireSha(headSha, 'HEAD');
  if (!Buffer.isBuffer(patch)) throw new Error('The complete cleanup patch must be a Buffer');
  let env = { ...process.env, GIT_OPTIONAL_LOCKS: '0' };
  const git = (args, input) => execFileSync('git', args, { cwd, env, input, maxBuffer: 256 * 1024 * 1024, stdio: ['pipe', 'pipe', 'pipe'] });
  // All paths must be considered even when the caller starts in a subdirectory.
  cwd = utf8(git(['rev-parse', '--show-toplevel'])).replace(/\n$/, '');
  const oid = (args) => git(args).toString('ascii').trim();
  const assertHead = () => { if (oid(['rev-parse', '--verify', 'HEAD']) !== headSha) throw new Error('Checkout HEAD differs from the analyzed HEAD'); };
  assertHead();
  const baseTreeSha = oid(['rev-parse', '--verify', `${headSha}^{tree}`]);
  const sourceIndex = utf8(git(['rev-parse', '--path-format=absolute', '--git-path', 'index'])).replace(/\n$/, '');
  const temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'cleanup-proposal-index-'));
  env = { ...env, GIT_INDEX_FILE: path.join(temporary, 'index') };
  try {
    git(['read-tree', headSha]);
    git(['-c', 'apply.ignoreWhitespace=false', 'apply', '--cached', '--whitespace=nowarn', '-'], patch);
    const cleanedTreeSha = oid(['write-tree']);
    // Start from the caller's index, not HEAD: on core.filemode=false
    // filesystems, staged executable bits cannot be recovered from stat().
    // Only the disposable copy is staged; the original index stays untouched.
    if (fs.existsSync(sourceIndex)) fs.copyFileSync(sourceIndex, env.GIT_INDEX_FILE);
    else git(['read-tree', '--empty']);
    git(['add', '-A', '--', '.']);
    if (oid(['write-tree']) !== cleanedTreeSha) throw new Error('The patch does not describe the complete committable worktree tree');
    if (cleanedTreeSha === baseTreeSha) throw new Error('No cleanup tree changes were captured');

    // Git's NUL-delimited raw metadata supplies paths and objects; Git also
    // produces each complete file diff. No unified diff hunks are interpreted.
    const records = utf8(git(['diff-tree', '--raw', '-r', '-z', '--no-renames', '--no-abbrev', '--no-commit-id', baseTreeSha, cleanedTreeSha])).split('\0');
    const changes = [], files = [];
    for (let index = 0; index < records.length - 1; index += 2) {
      const metadata = records[index].match(/^:(\d{6}) (\d{6}) ([a-f0-9]+) ([a-f0-9]+) ([A-Z])(?:\d+)?$/);
      if (!metadata || records[index + 1] === undefined) throw new Error('Invalid Git raw change metadata');
      const [, oldMode, newMode, , newSha] = metadata, file = records[index + 1];
      requirePath(file);
      const deleted = newMode === '000000', mode = deleted ? oldMode : newMode;
      requireMode(mode);
      if (oldMode !== '000000') requireMode(oldMode);
      const change = { path: file, mode, blobSha: deleted ? null : newSha };
      if (!deleted) change.contentBase64 = git(['cat-file', 'blob', newSha]).toString('base64');
      changes.push(change);
      const paths = [baseTreeSha, cleanedTreeSha, '--', `:(literal)${file}`];
      const diff = git(['diff', '--binary', '--no-ext-diff', '--no-textconv', '--no-renames', '--src-prefix=a/', '--dst-prefix=b/', ...paths]);
      const counts = git(['diff', '--numstat', '-z', '--no-ext-diff', '--no-textconv', '--no-renames', ...paths]).toString('utf8').split('\t');
      const count = (value) => value === '-' ? null : Number(value || 0);
      const entry = { path: file, additions: count(counts[0]), deletions: count(counts[1]) };
      try { entry.diff = utf8(diff); } catch { entry.diff = diff.toString('base64'); entry.encoding = 'base64'; }
      files.push(entry);
    }
    assertHead();
    return { headSha, baseTreeSha, cleanedTreeSha, patchSha256: crypto.createHash('sha256').update(patch).digest('hex'), changes, files };
  } finally {
    fs.rmSync(temporary, { recursive: true, force: true });
  }
}

async function ensureProposal({ github, repo, review, pullNumber, assertCurrentHead }) {
  const { headSha, baseTreeSha, cleanedTreeSha, changes } = review;
  for (const [label, sha] of Object.entries({ HEAD: headSha, baseTree: baseTreeSha, cleanedTree: cleanedTreeSha })) requireSha(sha, label);
  if (!Number.isSafeInteger(pullNumber) || pullNumber < 1 || typeof assertCurrentHead !== 'function') throw new Error('A pull request and exact-head check are required');
  if (!Array.isArray(changes) || !changes.length) throw new Error('A proposal must contain complete changes');
  const paths = new Set();
  const entries = changes.map(({ path: file, mode, blobSha, contentBase64 }) => {
    requirePath(file); requireMode(mode);
    if (paths.has(file)) throw new Error('Duplicate proposal path');
    paths.add(file);
    if (blobSha === null) return { path: file, mode, type: 'blob', sha: null };
    requireSha(blobSha, 'blob');
    if (typeof contentBase64 !== 'string') throw new Error('Missing blob bytes');
    const bytes = Buffer.from(contentBase64, 'base64');
    if (bytes.toString('base64') !== contentBase64) throw new Error('Invalid base64 blob bytes');
    const actual = crypto.createHash(blobSha.length === 40 ? 'sha1' : 'sha256').update(`blob ${bytes.length}\0`).update(bytes).digest('hex');
    if (actual !== blobSha) throw new Error('Captured blob SHA does not match its bytes');
    try { return { path: file, mode, type: 'blob', content: utf8(bytes) }; }
    catch { return { path: file, mode, type: 'blob', sha: blobSha, contentBase64 }; }
  });
  const git = github.rest.git;
  const branch = `cleanup/pr-${pullNumber}/${headSha.slice(0, 12)}-${cleanedTreeSha.slice(0, 12)}`;
  const existing = async () => {
    let object;
    try { ({ data: { object } } = await git.getRef({ ...repo, ref: 'heads/' + branch })); }
    catch (error) { if (error.status === 404) return null; throw error; }
    if (object?.type !== 'commit') throw new Error('Conflicting proposal ref does not identify a commit');
    requireSha(object.sha, 'proposal commit');
    const { data: commit } = await git.getCommit({ ...repo, commit_sha: object.sha });
    if (commit.tree?.sha !== cleanedTreeSha || commit.parents?.length !== 1 || commit.parents[0].sha !== headSha) {
      throw new Error('Conflicting proposal branch: its tree or sole parent differs');
    }
    return { branch, commitSha: object.sha };
  };
  const found = await existing();
  if (found) return found;
  // UTF-8 blobs are written together by createTree. Binary blobs need base64
  // uploads; their identities and then the entire resulting tree are checked.
  for (const entry of entries) {
    if (entry.contentBase64 === undefined) continue;
    const { data } = await git.createBlob({ ...repo, content: entry.contentBase64, encoding: 'base64' });
    if (data.sha !== entry.sha) throw new Error('GitHub blob SHA differs from the captured blob');
    delete entry.contentBase64;
  }
  const { data: tree } = await git.createTree({ ...repo, base_tree: baseTreeSha, tree: entries });
  if (tree.sha !== cleanedTreeSha) throw new Error('GitHub tree SHA differs from the complete cleaned tree');
  const { data: commit } = await git.createCommit({ ...repo,
    message: `${review.toolName || 'sandbox-cleanup'}: complete cleanup proposal for #${pullNumber}\n\nAnalyzed head: ${headSha}`,
    tree: cleanedTreeSha, parents: [headSha] });
  requireSha(commit.sha, 'created commit');
  await assertCurrentHead();
  try {
    await git.createRef({ ...repo, ref: 'refs/heads/' + branch, sha: commit.sha });
  } catch (error) {
    if (error.status !== 422) throw error;
    const raced = await existing();
    if (raced) return raced;
    throw error;
  }
  return { branch, commitSha: commit.sha };
}

module.exports = { captureProposal, ensureProposal };
