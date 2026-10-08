// Copyright (c) 2026 Carsten Hammer.
// SPDX-License-Identifier: EPL-2.0

'use strict';

const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { execFileSync } = require('node:child_process');

const MAX_BODY_BYTES = 55000;
const MAX_INLINE_COMMENTS = 30;
const SCHEMA_VERSION = 1;

function digest(value) {
  return crypto.createHash('sha256').update(value).digest('hex');
}

function validateHead(headSha) {
  if (!/^(?:[a-f0-9]{40}|[a-f0-9]{64})$/.test(headSha || '')) {
    throw new Error('REVIEW_HEAD_SHA must be a full lowercase Git commit SHA');
  }
}

function validateScope(scope) {
  if (!['run', 'file'].includes(scope)) {
    throw new Error('REVIEW_SUGGESTION_SCOPE must be run or file');
  }
}

function utf8(buffer) {
  const value = buffer.toString('utf8');
  if (!Buffer.from(value).equals(buffer)) throw new Error('Non-UTF-8 source requires the complete patch');
  return value;
}

// Git quotes unusual path bytes using C escapes, including octal UTF-8 bytes.
function decodeGitPath(value) {
  if (!value.startsWith('"')) return value;
  if (!value.endsWith('"')) throw new Error('Unterminated quoted Git path');
  const bytes = [];
  const escapes = { a: 7, b: 8, t: 9, n: 10, v: 11, f: 12, r: 13, '\\': 92, '"': 34 };
  const content = value.slice(1, -1);
  for (let index = 0; index < content.length;) {
    if (content[index] !== '\\') {
      const character = String.fromCodePoint(content.codePointAt(index));
      bytes.push(...Buffer.from(character));
      index += character.length;
    } else {
      index += 1;
      const octal = content.slice(index).match(/^[0-7]{1,3}/);
      if (octal) {
        bytes.push(parseInt(octal[0], 8));
        index += octal[0].length;
      } else if (Object.hasOwn(escapes, content[index])) {
        bytes.push(escapes[content[index]]);
        index += 1;
      } else {
        throw new Error('Unsupported quoted Git path escape');
      }
    }
  }
  return utf8(Buffer.from(bytes));
}

function unprefix(value, prefix) {
  const decoded = decodeGitPath(value);
  if (decoded === '/dev/null') return null;
  if (!decoded.startsWith(prefix + '/')) throw new Error('Unexpected Git path prefix');
  const result = decoded.slice(2);
  if (!result || result.includes('\0')) throw new Error('Invalid Git path');
  return result;
}

function headerPaths(header) {
  const source = header.slice('diff --git '.length);
  const candidates = [];
  for (let index = 0; index < source.length; index += 1) {
    if (source[index] !== ' ') continue;
    try {
      const oldPath = unprefix(source.slice(0, index), 'a');
      const newPath = unprefix(source.slice(index + 1), 'b');
      candidates.push({ oldPath, newPath });
    } catch { /* A space inside a filename is not the path separator. */ }
  }
  return candidates.find((candidate) => candidate.oldPath === candidate.newPath)
    || (candidates.length === 1 ? candidates[0] : {});
}

function parseHunks(patch) {
  const lines = patch.split('\n');
  const hunks = [];
  let additions = 0;
  let deletions = 0;
  for (let index = 0; index < lines.length; index += 1) {
    if (!lines[index].startsWith('@@')) continue;
    const match = lines[index].match(/^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@(?:.*)$/);
    if (!match) throw new Error('Unsupported or truncated diff hunk header');
    const hunk = {
      oldStart: Number(match[1]), oldCount: Number(match[2] ?? 1),
      newStart: Number(match[3]), newCount: Number(match[4] ?? 1), lines: [],
    };
    if (![hunk.oldStart, hunk.oldCount, hunk.newStart, hunk.newCount].every(Number.isSafeInteger)
        || (hunk.oldCount > 0 && hunk.oldStart === 0) || (hunk.newCount > 0 && hunk.newStart === 0)) {
      throw new Error('Invalid diff hunk coordinates');
    }
    let oldCount = 0;
    let newCount = 0;
    while (oldCount < hunk.oldCount || newCount < hunk.newCount) {
      index += 1;
      const line = lines[index];
      if (line === '\\ No newline at end of file') continue;
      if (line === undefined || ![' ', '+', '-'].includes(line[0])) {
        throw new Error('Truncated diff hunk');
      }
      const kind = line[0];
      hunk.lines.push({ kind, text: line.slice(1) });
      if (kind !== '+') oldCount += 1;
      if (kind !== '-') newCount += 1;
      if (kind === '+') additions += 1;
      if (kind === '-') deletions += 1;
      if (oldCount > hunk.oldCount || newCount > hunk.newCount) throw new Error('Inconsistent diff hunk lengths');
    }
    // Extra body lines can indicate a truncated/malformed header or patch.
    let next = index + 1;
    if (lines[next] === '\\ No newline at end of file') next += 1;
    if (lines[next] && [' ', '+', '-'].includes(lines[next][0])) {
      throw new Error('Extra lines after diff hunk');
    }
    hunks.push(hunk);
  }
  if (!hunks.length) throw new Error('No complete text hunks are available');
  return { hunks, additions, deletions };
}

function splitFiles(patch) {
  const starts = [...patch.matchAll(/^diff --git .+$/gm)].map((match) => match.index);
  if (!starts.length || starts[0] !== 0) {
    return patch ? [{ path: 'Unparsed cleanup patch', diff: patch, reason: 'The patch format cannot be represented as native suggestions' }] : [];
  }
  return starts.map((start, index) => {
    const diff = patch.slice(start, starts[index + 1] ?? patch.length);
    const lines = diff.split('\n');
    let oldPath;
    let newPath;
    try {
      ({ oldPath, newPath } = headerPaths(lines[0]));
      const oldMarker = lines.find((line) => line.startsWith('--- '));
      const newMarker = lines.find((line) => line.startsWith('+++ '));
      if (oldMarker) oldPath = unprefix(oldMarker.slice(4), 'a');
      if (newMarker) newPath = unprefix(newMarker.slice(4), 'b');
      const file = { path: newPath || oldPath || `Unparsed file ${index + 1}`, oldPath, newPath, diff };
      if (/^(?:new file mode|deleted file mode|old mode|new mode|rename from|rename to|copy from|copy to) /m.test(diff)
          || oldPath !== newPath || !oldPath || !newPath) {
        file.reason = 'File additions, deletions, renames, and mode changes require the complete patch';
      } else if (/^(?:GIT binary patch|Binary files )/m.test(diff)) {
        file.reason = 'Binary changes require the complete patch';
      } else if (/^index .* (?!100644$|100755$)\d+$/m.test(diff)) {
        file.reason = 'Non-regular files require the complete patch';
      } else if (diff.includes('\r') || diff.includes('\\ No newline at end of file')) {
        file.reason = 'Line endings cannot be preserved by a native suggestion';
      } else if (!oldMarker || !newMarker) {
        file.reason = 'No complete text patch is available';
      }
      return file;
    } catch (error) {
      return { path: newPath || oldPath || `Unparsed file ${index + 1}`, diff, reason: error.message };
    }
  });
}

function envelopeFor(file, readOriginal) {
  const original = utf8(Buffer.from(readOriginal(file.oldPath)));
  if (original.includes('\r') || (original && !original.endsWith('\n'))) {
    throw new Error('Source line endings cannot be preserved by a native suggestion');
  }
  const before = original ? original.slice(0, -1).split('\n') : [];
  const after = [];
  let cursor = 0;
  for (const hunk of parseHunks(file.diff).hunks) {
    const oldIndex = hunk.oldCount === 0 ? hunk.oldStart : hunk.oldStart - 1;
    const newIndex = hunk.newCount === 0 ? hunk.newStart : hunk.newStart - 1;
    if (oldIndex < cursor || oldIndex > before.length) throw new Error('Overlapping or out-of-range cleanup hunks');
    after.push(...before.slice(cursor, oldIndex));
    if (after.length !== newIndex) throw new Error('Inconsistent cleanup hunk positions');
    cursor = oldIndex;
    for (const line of hunk.lines) {
      if (line.kind !== '+') {
        if (before[cursor] !== line.text) throw new Error('Cleanup patch does not match the analyzed head');
        cursor += 1;
      }
      if (line.kind !== '-') after.push(line.text);
    }
  }
  after.push(...before.slice(cursor));
  let start = 0;
  while (start < before.length && start < after.length && before[start] === after[start]) start += 1;
  let end = before.length;
  let afterEnd = after.length;
  while (end > start && afterEnd > start && before[end - 1] === after[afterEnd - 1]) {
    end -= 1;
    afterEnd -= 1;
  }
  if (start === end && start === afterEnd) throw new Error('No source replacement is required');
  let replacement = after.slice(start, afterEnd);
  if (start === end) {
    // GitHub replaces existing lines. Include an unchanged anchor for insertions.
    if (!before.length) throw new Error('An empty file has no existing line to anchor an insertion');
    if (start < before.length) {
      replacement.push(before[start]);
      end += 1;
    } else {
      start -= 1;
      replacement.unshift(before[start]);
    }
  }
  return { startLine: start + 1, endLine: end, text: replacement.join('\n'), replacementLineCount: replacement.length, originalLines: before.slice(start, end) };
}

function prepareReview({ patch, headSha, toolName = 'sandbox-cleanup', scope = 'run', readOriginal }) {
  validateHead(headSha);
  validateScope(scope);
  const patchBuffer = Buffer.isBuffer(patch) ? patch : Buffer.from(patch);
  const patchSha256 = digest(patchBuffer);
  let patchText;
  let patchBase64;
  try {
    patchText = utf8(patchBuffer);
  } catch {
    patchBase64 = patchBuffer.toString('base64');
    patchText = '';
  }
  const files = patchBase64
    ? [{ path: 'Non-UTF-8 cleanup patch', diff: patchBase64, encoding: 'base64', reason: 'The lossless patch is encoded as base64; native suggestions are unavailable' }]
    : splitFiles(patchText);
  for (const file of files) {
    if (file.reason) continue;
    try {
      file.envelope = envelopeFor(file, readOriginal);
    } catch (error) {
      file.reason = error.message;
    }
  }
  const marker = `<!-- sandbox-cleanup-review:v1:${headSha}:${patchSha256}:${digest(toolName + '\0' + scope).slice(0, 16)} -->`;
  return { schemaVersion: SCHEMA_VERSION, headSha, toolName, scope, patchSha256, patch: patchText, ...(patchBase64 ? { patchBase64 } : {}), marker, files };
}

function codeFence(text, language = '', lineCount) {
  let length = 3;
  for (const match of text.matchAll(/`+/g)) length = Math.max(length, match[0].length + 1);
  const fence = '`'.repeat(length);
  // A suggestion replaces a list of lines. Its final fence separator is not a
  // replacement line: [] deletes, [''] keeps one blank line, and ['new', '']
  // must retain its trailing blank line. Raw diff blocks already carry EOLs.
  const separator = lineCount === undefined
    ? (text === '' || text.endsWith('\n') ? '' : '\n')
    : (lineCount > 0 ? '\n' : '');
  return `${fence}${language}\n${text}${separator}${fence}`;
}

function inlineCode(text) {
  const value = String(text).replace(/[\r\n\t]/g, (character) => ({ '\r': '\\r', '\n': '\\n', '\t': '\\t' })[character]);
  let length = 1;
  for (const match of value.matchAll(/`+/g)) length = Math.max(length, match[0].length + 1);
  const fence = '`'.repeat(length);
  return `${fence} ${value} ${fence}`;
}

function suggestionFor(file, prFile) {
  if (!prFile || typeof prFile.patch !== 'string' || !prFile.patch || prFile.patch.includes('\r')) {
    throw new Error('The PR has no complete text patch for this file');
  }
  const parsed = parseHunks(prFile.patch);
  if ((Number.isInteger(prFile.additions) && prFile.additions !== parsed.additions)
      || (Number.isInteger(prFile.deletions) && prFile.deletions !== parsed.deletions)) {
    throw new Error('The PR patch is truncated or inconsistent with its file totals');
  }
  const { startLine, endLine, text, replacementLineCount, originalLines } = file.envelope;
  if (!Number.isSafeInteger(replacementLineCount) || replacementLineCount < 0) {
    throw new Error('The complete replacement line count is unavailable');
  }
  const hunk = parsed.hunks.find((item) => item.newCount > 0 && startLine >= item.newStart && endLine < item.newStart + item.newCount);
  if (!hunk) throw new Error('All required edits do not fit within one PR diff hunk');
  const prLines = new Map();
  let lineNumber = hunk.newStart;
  for (const line of hunk.lines) {
    if (line.kind !== '-') {
      prLines.set(lineNumber, line.text);
      lineNumber += 1;
    }
  }
  for (let line = startLine; line <= endLine; line += 1) {
    if (prLines.get(line) !== originalLines[line - startLine]) {
      throw new Error('The PR patch context does not match the analyzed source');
    }
  }
  const range = startLine === endLine ? `line ${startLine}` : `lines ${startLine}–${endLine}`;
  const body = [
    '**Apply all cleanup changes in this file**',
    'Click **Commit suggestion** below, then **Commit changes**. This accepts every cleanup edit in this file together.',
    `GitHub replaces **${range}** as one block. Unchanged lines between the edits are included to keep the changes together. The rest of the file, including any closing braces outside this range, stays as it is.`,
    codeFence(text, 'suggestion', replacementLineCount),
  ].join('\n\n');
  if (Buffer.byteLength(body) > MAX_BODY_BYTES) throw new Error('The complete file replacement exceeds the inline comment limit');
  return {
    path: file.path, side: 'RIGHT', line: endLine, body,
    ...(startLine < endLine ? { start_line: startLine, start_side: 'RIGHT' } : {}),
  };
}

function planReview(review, prFiles, { maxComments = MAX_INLINE_COMMENTS } = {}) {
  const filesByPath = new Map(prFiles.map((file) => [file.filename, file]));
  const comments = [];
  const decisions = [];
  for (const file of review.files) {
    let reason = file.reason;
    if (!reason && review.scope === 'run' && review.files.length !== 1) {
      reason = 'Apply all files together as one run; no independently selectable file suggestions';
    }
    if (!reason && !file.envelope) reason = 'No complete replacement could be reconstructed';
    if (!reason) {
      try {
        const comment = suggestionFor(file, filesByPath.get(file.path));
        if (comments.length < maxComments) {
          comments.push(comment);
          decisions.push({ path: file.path, inline: true, reason: 'One native suggestion contains every cleanup edit in this file' });
          continue;
        }
        reason = `The inline comment limit (${maxComments}) was reached; use the complete patch`;
      } catch (error) {
        reason = error.message;
      }
    }
    decisions.push({ path: file.path, inline: false, reason });
  }
  return { comments, decisions, inlineLimit: maxComments };
}

function safeUrl(value) {
  if (!value) return '';
  const url = new URL(value);
  if (!['https:', 'http:'].includes(url.protocol)) throw new Error('Review links must use HTTP or HTTPS');
  return url.href.replace(/>/g, '%3E').replace(/</g, '%3C');
}

function buildReviewBody(review, plan, { artifactUrl = '', runUrl = '', maxBytes = MAX_BODY_BYTES, fallbackReason = '' } = {}) {
  const artifact = safeUrl(artifactUrl);
  const run = safeUrl(runUrl);
  const header = [
    `## ${inlineCode(review.toolName)} cleanup review`,
    `Changed files: **${review.files.length}**.${plan.pending ? '' : ` Directly applicable file suggestions: **${plan.comments.length}**.`}`,
    ...(plan.comments.length ? ['**To accept a suggestion:** open its review comment under **Files changed**, click **Commit suggestion**, then **Commit changes**. Each suggestion contains all cleanup edits for that file.'] : []),
    artifact
      ? `**${plan.comments.length ? 'Alternative: apply' : 'Apply'} the whole cleanup result:** [download the complete patch and reports](<${artifact}>), check out the analyzed commit listed below, and apply \`suggestions.patch\`. This patch already includes every suggestion in this review.`
      : 'The complete patch is included below for application to the analyzed commit listed under Analysis details. It already includes every suggestion in this review.',
    review.scope === 'file'
      ? 'Files are independent; changes within each file must be applied together.'
      : 'All files in this cleanup run must be applied together.',
    ...(fallbackReason ? [`**Native suggestions unavailable:** ${fallbackReason}`] : []),
  ].join('\n\n');
  const sections = review.files.map((file, index) => [
    `### ${inlineCode(path.posix.basename(file.path))}`,
    plan.decisions[index]?.inline === true
      ? '**Suggestion available:** use **Commit suggestion** in this file\'s review comment.'
      : plan.pending ? 'Native suggestion availability is shown in the published review.'
        : '**Complete patch required:** this file has no directly applicable suggestion.',
    '<details>\n<summary>View complete file diff (view only)</summary>',
    inlineCode(file.path),
    plan.decisions[index]?.reason || file.reason || 'Complete file patch',
    codeFence(file.diff, file.encoding === 'base64' ? 'base64' : 'diff'),
    '</details>',
  ].join('\n\n'));
  const details = [
    '<details>\n<summary>Analysis details</summary>',
    `Analyzed head: ${inlineCode(review.headSha)}.`,
    `Patch SHA-256: ${inlineCode(review.patchSha256)}.`,
    ...(run ? [`[Workflow run](<${run}>)`] : []),
    '</details>',
  ].join('\n\n');
  const complete = `${header}\n\n${sections.join('\n\n')}\n\n${details}\n\n${review.marker}\n`;
  if (Buffer.byteLength(complete) <= maxBytes) return complete;
  if (!artifact) {
    throw new Error('The complete review exceeds the GitHub comment limit and no uploaded artifact URL is available; refusing to publish a truncated cleanup fix');
  }
  const prefix = [
    `## ${inlineCode(review.toolName)} cleanup review`,
    `Analyzed head: ${inlineCode(review.headSha)}. Changed files: **${review.files.length}**.`,
    '**This review is truncated because the complete grouped diff exceeds the GitHub comment limit.**',
    `[Download the complete patch, review, and reports](<${artifact}>) before applying the fix.`,
    `Complete file suggestions offered inline: **${plan.comments.length}** (limit ${plan.inlineLimit ?? MAX_INLINE_COMMENTS}). Remaining file changes: **${review.files.length - plan.comments.length}**, retained in the complete patch.`,
    review.scope === 'file' ? 'Apply every edit for a selected file together.' : 'Apply all files in this cleanup run together as one fix unit.',
    ...(fallbackReason ? [`**Native suggestions unavailable:** ${fallbackReason}`] : []),
    ...(run ? [`[Workflow run](<${run}>)`] : []),
    '### Changed files',
  ].join('\n\n') + '\n\n';
  const suffix = `\n\n${review.marker}\n`;
  let listing = '';
  let shown = 0;
  for (const file of review.files) {
    const line = `- ${inlineCode(file.path)}\n`;
    const reserve = `\n${review.files.length} additional file names are listed in the complete review artifact.`;
    if (Buffer.byteLength(prefix + listing + line + reserve + suffix) > maxBytes) break;
    listing += line;
    shown += 1;
  }
  if (shown < review.files.length) listing += `\n${review.files.length - shown} additional file names are listed in the complete review artifact.`;
  const limited = prefix + listing + suffix;
  if (Buffer.byteLength(limited) > maxBytes) throw new Error('The review metadata exceeds the comment limit');
  return limited;
}

function required(env, name) {
  if (!env[name]) throw new Error(`${name} is required`);
  return env[name];
}

function prepareFromEnvironment(env = process.env) {
  const headSha = required(env, 'REVIEW_HEAD_SHA');
  validateHead(headSha);
  const outputDir = required(env, 'REVIEW_OUTPUT_DIR');
  const patchFile = required(env, 'REVIEW_PATCH_FILE');
  const currentHead = execFileSync('git', ['rev-parse', '--verify', 'HEAD'], { encoding: 'utf8' }).trim();
  if (currentHead !== headSha) throw new Error(`Checkout head ${currentHead} differs from analyzed head ${headSha}`);
  const review = prepareReview({
    patch: fs.readFileSync(patchFile), headSha,
    toolName: env.REVIEW_TOOL_NAME || 'sandbox-cleanup',
    scope: env.REVIEW_SUGGESTION_SCOPE || 'run',
    readOriginal: (filename) => execFileSync('git', ['show', `${headSha}:${filename}`], { maxBuffer: 64 * 1024 * 1024, stdio: ['ignore', 'pipe', 'pipe'] }),
  });
  fs.mkdirSync(outputDir, { recursive: true });
  fs.writeFileSync(path.join(outputDir, 'review.json'), JSON.stringify(review, null, 2) + '\n');
  const pendingPlan = { pending: true, comments: [], decisions: review.files.map((file) => ({ reason: file.reason || 'Complete file patch; native placement is evaluated against the current PR diff when publishing' })) };
  fs.writeFileSync(path.join(outputDir, 'review.md'), buildReviewBody(review, pendingPlan, { maxBytes: Infinity }));
  return review;
}

async function publish({ github, context, core, env = process.env }) {
  const review = JSON.parse(fs.readFileSync(path.join(required(env, 'REVIEW_OUTPUT_DIR'), 'review.json'), 'utf8'));
  const headSha = required(env, 'REVIEW_HEAD_SHA');
  const scope = env.REVIEW_SUGGESTION_SCOPE || 'run';
  const toolName = env.REVIEW_TOOL_NAME || 'sandbox-cleanup';
  validateHead(headSha);
  validateScope(scope);
  if (review.schemaVersion !== SCHEMA_VERSION || review.headSha !== headSha || review.scope !== scope || review.toolName !== toolName) {
    throw new Error('Prepared review metadata does not match the requested head, tool, or suggestion scope');
  }
  const rawPatch = review.patchBase64 ? Buffer.from(review.patchBase64, 'base64') : Buffer.from(review.patch);
  if (digest(rawPatch) !== review.patchSha256) throw new Error('Prepared cleanup patch checksum does not match');
  const pullNumber = context.payload?.pull_request?.number || context.issue?.number;
  if (!Number.isSafeInteger(pullNumber) || pullNumber < 1) throw new Error('A pull request number is required to publish cleanup review');
  const parameters = { ...context.repo, pull_number: pullNumber };
  const [prFiles, reviews] = await Promise.all([
    github.paginate(github.rest.pulls.listFiles, { ...parameters, per_page: 100 }),
    github.paginate(github.rest.pulls.listReviews, { ...parameters, per_page: 100 }),
  ]);
  const assertCurrentHead = async () => {
    const { data: pr } = await github.rest.pulls.get(parameters);
    if (pr.head.sha !== headSha) throw new Error(`Refusing stale cleanup review: PR head is ${pr.head.sha}, analyzed head is ${headSha}`);
  };
  if (reviews.some((item) => item.user?.type === 'Bot' && item.user?.login === 'github-actions[bot]'
      && item.state !== 'PENDING' && item.body?.includes(review.marker))) {
    await assertCurrentHead();
    core.info('The GitHub Actions bot already published this exact cleanup patch for this head');
    return { duplicate: true };
  }
  const plan = planReview(review, prFiles);
  const server = context.serverUrl || env.GITHUB_SERVER_URL || 'https://github.com';
  const runId = context.runId || env.GITHUB_RUN_ID;
  const runUrl = runId ? `${server}/${encodeURIComponent(context.repo.owner)}/${encodeURIComponent(context.repo.repo)}/actions/runs/${runId}` : '';
  const bodyOptions = { artifactUrl: env.REVIEW_ARTIFACT_URL || '', runUrl };
  const body = buildReviewBody(review, plan, bodyOptions);
  const request = { ...parameters, commit_id: headSha, event: 'COMMENT', body, ...(plan.comments.length ? { comments: plan.comments } : {}) };
  await assertCurrentHead();
  try {
    const result = await github.rest.pulls.createReview(request);
    core.info(`Published complete cleanup review with ${plan.comments.length} complete file suggestions`);
    return { review: result.data, inlineComments: plan.comments.length };
  } catch (error) {
    if (error.status !== 422 || !plan.comments.length) throw error;
    core.warning('GitHub rejected inline placement; publishing the complete grouped patch without inline suggestions');
    const fallbackPlan = {
      comments: [],
      decisions: plan.decisions.map((decision) => decision.inline
        ? { ...decision, inline: false, reason: 'GitHub rejected inline placement; apply this complete file patch together' }
        : decision),
    };
    const fallbackBody = buildReviewBody(review, fallbackPlan, { ...bodyOptions, fallbackReason: 'GitHub rejected the inline ranges. Apply the complete grouped patch below or from the artifact.' });
    await assertCurrentHead();
    const result = await github.rest.pulls.createReview({ ...parameters, commit_id: headSha, event: 'COMMENT', body: fallbackBody });
    return { review: result.data, inlineComments: 0, fallback: true };
  }
}

module.exports = { prepareReview, prepareFromEnvironment, planReview, buildReviewBody, publish };

if (require.main === module) {
  try {
    if (process.argv.length !== 3 || process.argv[2] !== '--prepare') throw new Error('Usage: node cleanup-review.cjs --prepare');
    const review = prepareFromEnvironment();
    process.stdout.write(`Prepared complete cleanup review for ${review.files.length} file(s) at ${review.headSha}\n`);
  } catch (error) {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  }
}
