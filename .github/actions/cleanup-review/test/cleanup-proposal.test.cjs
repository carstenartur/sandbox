// Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const { execFileSync } = require('node:child_process');
const { test } = require('node:test');
const { captureProposal, ensureProposal } = require('../cleanup-proposal.cjs');

const hash = (bytes) => crypto.createHash('sha256').update(bytes).digest('hex');
const blob = (bytes) => crypto.createHash('sha1').update(`blob ${bytes.length}\0`).update(bytes).digest('hex');
const padding = Array.from({ length: 12 }, (_, i) => `  // unchanged ${i}\n`).join('');
const original = 'import java.nio.charset.Charset;\nclass A {\n' + padding
  + '  String first(byte[] bytes) { return new String(bytes, Charset.forName("UTF-8")); }\n' + padding
  + '  String last(byte[] bytes) { return new String(bytes, Charset.forName("UTF-8")); }\n}\n';

function repository(t) {
  const cwd = fs.mkdtempSync(path.join(os.tmpdir(), 'cleanup-proposal-'));
  t.after(() => fs.rmSync(cwd, { recursive: true, force: true }));
  const git = (...args) => execFileSync('git', args, { cwd, env: { ...process.env, GIT_OPTIONAL_LOCKS: '0' }, stdio: ['pipe', 'pipe', 'pipe'] });
  const write = (name, bytes) => fs.writeFileSync(path.join(cwd, name), bytes);
  git('init', '-q', '--object-format=sha1');
  git('config', 'user.name', 'Cleanup Test'); git('config', 'user.email', 'cleanup@example.test');
  git('config', 'core.autocrlf', 'false'); git('config', 'core.filemode', 'true');
  const baseline = { 'A.java': original, 'Other file.java': 'old companion\n', 'Gone.java': 'delete me\n',
    'Bytes.java': Buffer.from([0, 1, 255]), 'Crlf.java': 'old\r\n', 'Executable.java': 'same\n', 'Untouched.java': 'retained\n' };
  for (const [name, bytes] of Object.entries(baseline)) write(name, bytes);
  git('add', '.'); git('commit', '-qm', 'Analyzed head');
  const headSha = git('rev-parse', 'HEAD').toString().trim();
  const patch = () => git('diff', '--binary', '--no-ext-diff', '--no-textconv', '--src-prefix=a/', '--dst-prefix=b/');
  return { cwd, git, write, headSha, patch, index: () => fs.readFileSync(path.join(cwd, '.git/index')) };
}

function completeCleanup(t) {
  const r = repository(t);
  const expected = { 'A.java': original.replace('import java.nio.charset.Charset;', 'import java.nio.charset.StandardCharsets;')
    .replaceAll('Charset.forName("UTF-8")', 'StandardCharsets.UTF_8'), 'Other file.java': 'new companion\n',
  'Added.java': 'new source\n', 'Empty.java': '', 'Bytes.java': Buffer.from([0, 255, 2, 128]), 'Crlf.java': 'new\r\n' };
  for (const [name, bytes] of Object.entries(expected)) r.write(name, bytes);
  fs.unlinkSync(path.join(r.cwd, 'Gone.java'));
  fs.chmodSync(path.join(r.cwd, 'Executable.java'), 0o755);
  fs.symlinkSync('Other file.java', path.join(r.cwd, 'Link.java'));
  r.git('add', '--intent-to-add', '--', 'Added.java', 'Empty.java', 'Link.java');
  return { ...r, expected };
}

test('capture keeps every distant and companion edit, addition, deletion, binary byte, CRLF, mode and symlink', (t) => {
  const r = completeCleanup(t), patch = r.patch(), index = r.index();
  const review = captureProposal({ headSha: r.headSha, patch, cwd: r.cwd });
  assert.equal(review.headSha, r.headSha);
  assert.equal(review.baseTreeSha, r.git('rev-parse', 'HEAD^{tree}').toString().trim());
  assert.equal(review.patchSha256, hash(patch));
  const changes = Object.fromEntries(review.changes.map((change) => [change.path, change]));
  assert.deepEqual(Object.keys(changes).sort(), [...Object.keys(r.expected), 'Gone.java', 'Executable.java', 'Link.java'].sort());
  for (const [name, bytes] of Object.entries({ ...r.expected, 'Executable.java': 'same\n', 'Link.java': 'Other file.java' })) {
    const expected = Buffer.from(bytes), change = changes[name];
    assert.deepEqual(Buffer.from(change.contentBase64, 'base64'), expected, name);
    assert.equal(change.blobSha, blob(expected), name);
    assert.equal(change.mode, name === 'Executable.java' ? '100755' : name === 'Link.java' ? '120000' : '100644');
    assert.deepEqual(r.git('cat-file', 'blob', `${review.cleanedTreeSha}:${name}`), expected);
  }
  assert.equal(changes['Gone.java'].blobSha, null);
  assert.deepEqual(r.git('cat-file', 'blob', `${review.cleanedTreeSha}:Untouched.java`), Buffer.from('retained\n'));
  const files = Object.fromEntries(review.files.map((file) => [file.path, file]));
  assert.deepEqual(Object.keys(files).sort(), Object.keys(changes).sort());
  assert.equal(files['A.java'].diff.match(/^@@/gm).length, 3, 'All three distant hunks remain in the full diff');
  assert.match(files['A.java'].diff, /\+import java.nio.charset.StandardCharsets;/);
  assert.equal(files['A.java'].additions, 3); assert.equal(files['A.java'].deletions, 3);
  assert.equal(files['Bytes.java'].additions, null); assert.equal(files['Bytes.java'].deletions, null);
  assert.match(files['Bytes.java'].diff, /GIT binary patch/);
  assert.match(files['Executable.java'].diff, /new mode 100755/);
  assert.deepEqual(r.index(), index, 'The real index, including intent-to-add entries, stays byte-for-byte unchanged');
  assert.deepEqual(r.patch(), patch, 'The worktree and its complete patch stay unchanged');
});

test('non-UTF-8 source diffs use lossless base64 without rejecting valid UTF-8 paths', (t) => {
  const r = repository(t), bytes = Buffer.from([0xff, 0x0a]);
  r.write('Ä.java', bytes); r.git('add', '-N', '--', 'Ä.java');
  const review = captureProposal({ headSha: r.headSha, patch: r.patch(), cwd: r.cwd });
  assert.equal(review.files[0].encoding, 'base64');
  assert.ok(Buffer.from(review.files[0].diff, 'base64').includes(bytes));
  assert.deepEqual(Buffer.from(review.changes[0].contentBase64, 'base64'), bytes);
});

for (const [name, mutate] of [
  ['another changed file', (r) => r.write('Other file.java', 'omitted\n')],
  ['a new Java file', (r) => r.write('Missing.java', 'omitted\n')],
  ['a removed Java file', (r) => fs.unlinkSync(path.join(r.cwd, 'Gone.java'))],
  ['a mode-only change', (r) => fs.chmodSync(path.join(r.cwd, 'Executable.java'), 0o755)],
  ['a non-Java change', (r) => r.write('notes.txt', 'omitted\n')],
  ['a staged change', (r) => { r.write('Other file.java', 'staged\n'); r.git('add', '--', 'Other file.java'); }],
]) test(`capture rejects a patch omitting ${name} and preserves the index on failure`, (t) => {
  const r = repository(t); r.write('A.java', 'changed\n'); const patch = r.patch(); mutate(r);
  const index = r.index();
  assert.throws(() => captureProposal({ headSha: r.headSha, patch, cwd: r.cwd }), /tree|worktree|complete/i);
  assert.deepEqual(r.index(), index);
});

test('capture rejects the wrong analyzed head and an inapplicable patch', (t) => {
  const r = repository(t); r.write('A.java', 'changed\n'); const patch = r.patch(), index = r.index();
  assert.throws(() => captureProposal({ headSha: '1'.repeat(40), patch, cwd: r.cwd }), /HEAD/i);
  assert.throws(() => captureProposal({ headSha: r.headSha, patch: Buffer.from('not a patch'), cwd: r.cwd }));
  assert.deepEqual(r.index(), index);
});

test('capture rejects non-Java paths even when the patch and whole tree agree', (t) => {
  const r = repository(t); r.write('notes.txt', 'new\n'); r.git('add', '-N', '--', 'notes.txt');
  assert.throws(() => captureProposal({ headSha: r.headSha, patch: r.patch(), cwd: r.cwd }), /Java|path/i);
});

test('capture rejects filename bytes that GitHub JSON cannot represent', (t) => {
  const r = repository(t);
  fs.writeFileSync(Buffer.concat([Buffer.from(r.cwd + '/'), Buffer.from([255]), Buffer.from('.java')]), 'new\n');
  r.git('add', '-N', '--', '.');
  assert.throws(() => captureProposal({ headSha: r.headSha, patch: r.patch(), cwd: r.cwd }), /UTF-8|path/i);
});

test('capture rejects Java-named gitlinks instead of silently turning them into blobs', (t) => {
  const r = repository(t), child = repository(t);
  fs.renameSync(child.cwd, path.join(r.cwd, 'Module.java'));
  r.git('add', '-N', '--', 'Module.java');
  assert.throws(() => captureProposal({ headSha: r.headSha, patch: r.patch(), cwd: r.cwd }), /mode|gitlink/i);
});

const HEAD = 'a'.repeat(40), BASE = 'b'.repeat(40), TREE = 'c'.repeat(40), COMMIT = 'd'.repeat(40);
function prepared() {
  const bytes = Buffer.from([0, 255, 13, 10]);
  return { headSha: HEAD, baseTreeSha: BASE, cleanedTreeSha: TREE, toolName: 'encoding-cleanup',
    changes: [{ path: 'A.java', mode: '100755', blobSha: blob(bytes), contentBase64: bytes.toString('base64') },
      { path: 'Gone.java', mode: '100644', blobSha: null }] };
}
function transport(options = {}) {
  const calls = [], git = {};
  let ref = options.existing;
  const invoke = (name, implementation) => { git[name] = async (args) => { calls.push([name, args]); return { data: await implementation(args) }; }; };
  invoke('getRef', () => { if (options.readError) throw options.readError;
    if (!ref) throw Object.assign(new Error('Missing ref'), { status: 404 });
    return { object: { type: options.type || 'commit', sha: ref } }; });
  invoke('getCommit', () => ({ tree: { sha: options.existingTree || TREE }, parents: options.parents || [{ sha: HEAD }] }));
  invoke('createBlob', (args) => { assert.equal(args.encoding, 'base64'); return { sha: options.blobSha || blob(Buffer.from(args.content, 'base64')) }; });
  invoke('createTree', () => ({ sha: options.treeSha || TREE }));
  invoke('createCommit', () => ({ sha: COMMIT }));
  invoke('createRef', (args) => { ref = args.sha; if (options.race) throw Object.assign(new Error('Already exists'), { status: 422 }); return { object: { sha: ref } }; });
  return { github: { rest: { git } }, calls };
}
async function ensure(api, overrides = {}) {
  return ensureProposal({ github: api.github, repo: { owner: 'owner', repo: 'repo' }, pullNumber: 42,
    review: prepared(), assertCurrentHead: async () => api.calls.push(['headCheck']), ...overrides });
}

test('publication creates one complete commit on a deterministic dedicated branch, then reuses it on retry', async () => {
  const api = transport(), result = await ensure(api);
  assert.deepEqual(result, { branch: `cleanup/pr-42/${HEAD.slice(0, 12)}-${TREE.slice(0, 12)}`, commitSha: COMMIT });
  assert.deepEqual(api.calls.find(([name]) => name === 'createTree')[1], { owner: 'owner', repo: 'repo', base_tree: BASE,
    tree: prepared().changes.map(({ path, mode, blobSha }) => ({ path, mode, type: 'blob', sha: blobSha })) });
  const commit = api.calls.find(([name]) => name === 'createCommit')[1];
  assert.deepEqual(commit.parents, [HEAD]); assert.equal(commit.tree, TREE);
  const refIndex = api.calls.findIndex(([name]) => name === 'createRef');
  assert.equal(api.calls[refIndex - 1][0], 'headCheck');
  assert.deepEqual(api.calls[refIndex][1], { owner: 'owner', repo: 'repo', ref: 'refs/heads/' + result.branch, sha: COMMIT });
  const count = api.calls.length;
  assert.deepEqual(await ensure(api), result);
  assert.deepEqual(api.calls.slice(count).map(([name]) => name), ['getRef', 'getCommit']);
});

test('UTF-8 files, CRLF, empty files and symlinks are batched into one tree request with exact contents', async () => {
  const review = prepared(), contents = ['é\r\n', '', 'Other file.java'];
  review.changes = contents.map((content, index) => ({ path: `File${index}.java`, mode: index === 2 ? '120000' : '100644',
    blobSha: blob(Buffer.from(content)), contentBase64: Buffer.from(content).toString('base64') }));
  const api = transport(); await ensure(api, { review });
  assert.ok(!api.calls.some(([name]) => name === 'createBlob'));
  const trees = api.calls.filter(([name]) => name === 'createTree');
  assert.equal(trees.length, 1);
  assert.deepEqual(trees[0][1].tree, review.changes.map(({ path, mode }, index) => ({ path, mode, type: 'blob', content: contents[index] })));
});

for (const options of [{ existingTree: 'e'.repeat(40) }, { parents: [] },
  { parents: [{ sha: HEAD }, { sha: HEAD }] }, { parents: [{ sha: 'f'.repeat(40) }] }, { type: 'tag' }]) {
  test(`an existing conflicting proposal is never overwritten: ${JSON.stringify(options)}`, async () => {
    const api = transport({ existing: COMMIT, ...options });
    await assert.rejects(ensure(api), /conflict|parent|tree|commit/i);
    assert.ok(api.calls.every(([name]) => ['getRef', 'getCommit'].includes(name)));
  });
}

test('a matching create-ref race is verified and reused, while a conflicting race fails', async () => {
  const api = transport({ race: true });
  assert.equal((await ensure(api)).commitSha, COMMIT);
  assert.deepEqual(api.calls.slice(-2).map(([name]) => name), ['getRef', 'getCommit']);
  await assert.rejects(ensure(transport({ race: true, existingTree: 'e'.repeat(40) })), /conflict|tree/i);
});

for (const status of [403, 500]) test(`getRef ${status} is not treated as a missing branch`, async () => {
  const error = Object.assign(new Error('Service refused'), { status }), api = transport({ readError: error });
  await assert.rejects(ensure(api), (actual) => actual === error);
  assert.deepEqual(api.calls.map(([name]) => name), ['getRef']);
});

for (const [option, forbidden] of [['blobSha', 'createTree'], ['treeSha', 'createCommit']]) {
  test(`a GitHub ${option} mismatch aborts before ${forbidden}`, async () => {
    const api = transport({ [option]: 'e'.repeat(40) });
    await assert.rejects(ensure(api), /SHA|hash|tree|blob/i);
    assert.ok(!api.calls.some(([name]) => [forbidden, 'createRef'].includes(name)));
  });
}

test('a head change detected immediately before createRef leaves no proposal ref', async () => {
  const api = transport();
  await assert.rejects(ensure(api, { assertCurrentHead: async () => { throw new Error('Stale HEAD'); } }), /Stale HEAD/);
  assert.ok(!api.calls.some(([name]) => name === 'createRef'));
});

test('corrupted blob bytes cannot be uploaded under the captured blob identity', async () => {
  const review = prepared(); review.changes[0].contentBase64 = Buffer.from('different').toString('base64');
  const api = transport();
  await assert.rejects(ensure(api, { review }), /SHA|hash|blob/i);
  assert.ok(!api.calls.some(([name]) => name.startsWith('create')));
});
