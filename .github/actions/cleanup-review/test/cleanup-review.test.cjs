// Copyright (c) 2026 Carsten Hammer.
// SPDX-License-Identifier: EPL-2.0

'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { test } = require('node:test');
const publisher = require('../cleanup-review.cjs');

const HEAD = 'a'.repeat(40);
const BEFORE = [
  'package example;',
  '',
  'import java.io.Reader;',
  '',
  'class Example {',
  '    String text(byte[] bytes) {',
  '        return new String(bytes, "UTF-8");',
  '    }',
  '}',
].join('\n') + '\n';
const IMPORT = 'import java.nio.charset.StandardCharsets;';
const CALL = '        return new String(bytes, StandardCharsets.UTF_8);';
const PATCH = [
  'diff --git a/src/Example.java b/src/Example.java',
  'index 1111111..2222222 100644',
  '--- a/src/Example.java',
  '+++ b/src/Example.java',
  '@@ -1,4 +1,5 @@',
  ' package example;',
  ' ',
  ' import java.io.Reader;',
  '+' + IMPORT,
  ' ',
  '@@ -6,3 +7,3 @@',
  '     String text(byte[] bytes) {',
  '-        return new String(bytes, "UTF-8");',
  '+' + CALL,
  '     }',
].join('\n') + '\n';

function prepare(patch = PATCH, originals = { 'src/Example.java': BEFORE }, options = {}) {
  return publisher.prepareReview({
    patch,
    headSha: HEAD,
    toolName: 'encoding-cleanup',
    readOriginal: (filename) => {
      assert.ok(Object.hasOwn(originals, filename), `Unexpected original lookup: ${filename}`);
      return Buffer.from(originals[filename]);
    },
    ...options,
  });
}

function fullPrFile(filename = 'src/Example.java', before = BEFORE) {
  const lines = before.slice(0, -1).split('\n');
  return {
    filename,
    status: 'modified',
    patch: `@@ -1,${lines.length} +1,${lines.length} @@\n-old first line\n+${lines[0]}\n`
      + lines.slice(1).map((line) => ' ' + line).join('\n') + '\n',
  };
}

function replacePatch(filename, oldText, newText) {
  return `diff --git a/${filename} b/${filename}\nindex 1111111..2222222 100644\n`
    + `--- a/${filename}\n+++ b/${filename}\n@@ -1 +1 @@\n-${oldText}\n+${newText}\n`;
}

test('one suggestion spans the import and distant call, preserving intervening source', () => {
  const review = prepare();
  const plan = publisher.planReview(review, [fullPrFile()]);
  assert.equal(plan.comments.length, 1);
  const comment = plan.comments[0];
  assert.equal(comment.start_line, 4);
  assert.equal(comment.line, 7);
  assert.equal(comment.path, 'src/Example.java');
  assert.equal(comment.side, 'RIGHT');
  assert.ok(comment.body.includes(`${IMPORT}\n\nclass Example {\n    String text(byte[] bytes) {\n${CALL}`));
  assert.equal((comment.body.match(/```suggestion\n/g) || []).length, 1);
});

test('unchanged lines inside one cleanup hunk do not create independent suggestions', () => {
  const combined = PATCH.replace('@@ -1,4 +1,5 @@', '@@ -1,8 +1,9 @@')
    .replace('@@ -6,3 +7,3 @@\n', ' class Example {\n');
  const plan = publisher.planReview(prepare(combined), [fullPrFile()]);
  assert.equal(plan.comments.length, 1);
  assert.equal(plan.comments[0].start_line, 4);
  assert.equal(plan.comments[0].line, 7);
  assert.ok(plan.comments[0].body.includes(IMPORT));
  assert.ok(plan.comments[0].body.includes(CALL));
});

test('off-diff companion import suppresses the entire suggestion, retaining its complete diff', () => {
  const review = prepare();
  const prFile = {
    filename: 'src/Example.java',
    patch: '@@ -6,3 +6,3 @@\n     String text(byte[] bytes) {\n-old call\n+        return new String(bytes, "UTF-8");\n     }\n',
  };
  const plan = publisher.planReview(review, [prFile]);
  assert.equal(plan.comments.length, 0);
  const body = publisher.buildReviewBody(review, plan, { artifactUrl: 'https://example.test/artifact' });
  assert.ok(body.includes('+' + IMPORT));
  assert.ok(body.includes('+' + CALL));
  assert.ok(body.includes('https://example.test/artifact'));
  assert.match(body, /complete patch/i);
});

test('the default run scope never offers partial fixes across files', () => {
  const other = replacePatch('Other.java', 'class Other {}', 'class Other { int value; }');
  const review = prepare(PATCH + other, { 'src/Example.java': BEFORE, 'Other.java': 'class Other {}\n' });
  assert.equal(review.scope, 'run');
  const plan = publisher.planReview(review, [fullPrFile(), fullPrFile('Other.java', 'class Other {}\n')]);
  assert.equal(plan.comments.length, 0);
  assert.match(publisher.buildReviewBody(review, plan), /together|one .*unit/i);
});

test('explicit file scope allows complete per-file suggestions', () => {
  const other = replacePatch('Other.java', 'class Other {}', 'class Other { int value; }');
  const review = prepare(PATCH + other, { 'src/Example.java': BEFORE, 'Other.java': 'class Other {}\n' }, { scope: 'file' });
  const plan = publisher.planReview(review, [fullPrFile(), fullPrFile('Other.java', 'class Other {}\n')]);
  assert.equal(plan.comments.length, 2);
  assert.ok(plan.comments[0].body.includes(IMPORT));
  assert.ok(plan.comments[0].body.includes(CALL));
});

test('a missing, truncated, or inconsistent PR patch cannot justify a native suggestion', () => {
  const review = prepare();
  for (const patch of [undefined, fullPrFile().patch.slice(0, -12), fullPrFile().patch.replace('class Example {', 'class Wrong {')]) {
    assert.equal(publisher.planReview(review, [{ filename: 'src/Example.java', patch }]).comments.length, 0);
  }
});

test('the replacement envelope must fit within one PR hunk, not disconnected contexts', () => {
  const review = prepare();
  const patch = '@@ -1,4 +1,4 @@\n-old package\n+package example;\n \n import java.io.Reader;\n \n'
    + '@@ -5,5 +5,5 @@\n class Example {\n     String text(byte[] bytes) {\n-old call\n+        return new String(bytes, "UTF-8");\n     }\n }\n';
  assert.equal(publisher.planReview(review, [{ filename: 'src/Example.java', patch }]).comments.length, 0);
});

test('new files, deleted files, mode changes, and binary changes remain complete patch entries', () => {
  const cases = [
    ['New.java', 'diff --git a/New.java b/New.java\nnew file mode 100644\n--- /dev/null\n+++ b/New.java\n@@ -0,0 +1 @@\n+class New {}\n'],
    ['Gone.java', 'diff --git a/Gone.java b/Gone.java\ndeleted file mode 100644\n--- a/Gone.java\n+++ /dev/null\n@@ -1 +0,0 @@\n-class Gone {}\n'],
    ['Mode.java', 'diff --git a/Mode.java b/Mode.java\nold mode 100644\nnew mode 100755\n'],
    ['Binary.java', 'diff --git a/Binary.java b/Binary.java\nindex 1111111..2222222 100644\nGIT binary patch\nliteral 3\nKcmZQzU|?Vb0000\n\nliteral 0\nHcmV?d00001\n'],
  ];
  const review = prepare(cases.map(([, patch]) => patch).join(''), {}, { scope: 'file' });
  assert.equal(review.files.length, cases.length);
  assert.equal(publisher.planReview(review, cases.map(([name]) => ({ filename: name, patch: '@@ -1 +1 @@\n-a\n+b\n' }))).comments.length, 0);
  const body = publisher.buildReviewBody(review, publisher.planReview(review, []));
  for (const [name, patch] of cases) {
    assert.ok(body.includes(name));
    assert.ok(body.includes(patch.trimEnd()));
  }
});

test('CRLF and missing final newlines are never normalized into suggestions', () => {
  for (const [before, patch] of [
    ['old\r\n', replacePatch('A.java', 'old\r', 'new\r')],
    ['old', replacePatch('A.java', 'old', 'new') + '\\ No newline at end of file\n'],
  ]) {
    const review = prepare(patch, { 'A.java': before });
    assert.equal(publisher.planReview(review, [fullPrFile('A.java', 'old\n')]).comments.length, 0);
    assert.ok(publisher.buildReviewBody(review, publisher.planReview(review, [])).includes(patch.trimEnd()));
  }
});

test('pure insertions at the beginning and end use an existing line as an exact anchor', () => {
  for (const [hunk, expected] of [
    ['@@ -0,0 +1 @@\n+import x.Y;\n', 'import x.Y;\nclass A {}'],
    ['@@ -1,0 +2 @@\n+// end\n', 'class A {}\n// end'],
  ]) {
    const patch = 'diff --git a/A.java b/A.java\n--- a/A.java\n+++ b/A.java\n' + hunk;
    const review = prepare(patch, { 'A.java': 'class A {}\n' });
    const plan = publisher.planReview(review, [fullPrFile('A.java', 'class A {}\n')]);
    assert.equal(plan.comments.length, 1);
    assert.equal(plan.comments[0].line, 1);
    assert.ok(plan.comments[0].body.includes(expected));
  }
});

test('a pure line deletion uses an empty suggestion, without introducing a blank line', () => {
  const before = 'class A {\n    // obsolete\n}\n';
  const patch = 'diff --git a/A.java b/A.java\n--- a/A.java\n+++ b/A.java\n@@ -2 +1,0 @@\n-    // obsolete\n';
  const review = prepare(patch, { 'A.java': before });
  const plan = publisher.planReview(review, [fullPrFile('A.java', before)]);
  assert.equal(plan.comments.length, 1);
  assert.equal(plan.comments[0].line, 2);
  assert.ok(plan.comments[0].body.endsWith('```suggestion\n```'));
});

test('replacement suggestions preserve an empty line and trailing blank lines distinctly from deletion', () => {
  for (const [replacement, hunk, expected] of [
    ['', '@@ -1 +1 @@\n-old\n+\n', '```suggestion\n\n```'],
    ['new\n', '@@ -1 +1,2 @@\n-old\n+new\n+\n', '```suggestion\nnew\n\n```'],
  ]) {
    const patch = 'diff --git a/A.java b/A.java\n--- a/A.java\n+++ b/A.java\n' + hunk;
    const review = prepare(patch, { 'A.java': 'old\n' });
    const plan = publisher.planReview(review, [fullPrFile('A.java', 'old\n')]);
    assert.equal(review.files[0].envelope.text, replacement);
    assert.equal(plan.comments.length, 1);
    assert.ok(plan.comments[0].body.endsWith(expected));
  }
});

test('PR addition/deletion totals catch a patch truncated at a complete hunk boundary', () => {
  const prFile = { ...fullPrFile(), additions: 2, deletions: 2 };
  assert.equal(publisher.planReview(prepare(), [prFile]).comments.length, 0);
});

test('quoted UTF-8 paths, spaces, and source backticks retain exact contents', () => {
  const filename = 'src/With Space-é.java';
  const patch = replacePatch(filename, 'old', '```suggestion').replace(`--- a/${filename}`, '--- "a/src/With Space-\\303\\251.java"')
    .replace(`+++ b/${filename}`, '+++ "b/src/With Space-\\303\\251.java"');
  const review = prepare(patch, { [filename]: 'old\n' });
  const plan = publisher.planReview(review, [fullPrFile(filename, 'old\n')]);
  assert.equal(plan.comments.length, 1);
  assert.equal(plan.comments[0].path, filename);
  assert.ok(plan.comments[0].body.includes('````suggestion\n```suggestion\n````'));
  assert.ok(publisher.buildReviewBody(review, plan).includes('````diff\n'));
});

test('malformed cleanup hunks fall back visibly instead of reconstructing partial source', () => {
  const patch = PATCH.replace('@@ -6,3 +7,3 @@', '@@ -6,4 +7,4 @@');
  const review = prepare(patch);
  assert.equal(publisher.planReview(review, [fullPrFile()]).comments.length, 0);
  assert.ok(publisher.buildReviewBody(review, publisher.planReview(review, [])).includes(patch.trimEnd()));
});

test('the inline comment cap leaves all remaining edits in the grouped review', () => {
  const originals = {};
  const prFiles = [];
  let patch = '';
  for (let index = 0; index < 4; index += 1) {
    const filename = `File${index}.java`;
    originals[filename] = 'old\n';
    patch += replacePatch(filename, 'old', `new${index}`);
    prFiles.push(fullPrFile(filename, 'old\n'));
  }
  const review = prepare(patch, originals, { scope: 'file' });
  const plan = publisher.planReview(review, prFiles, { maxComments: 2 });
  assert.equal(plan.comments.length, 2);
  const body = publisher.buildReviewBody(review, plan);
  assert.match(body, /limit|cap/i);
  assert.ok(body.includes('+new3'));
});

test('oversized review bodies explicitly defer to the complete artifact, respecting byte limits', () => {
  const patch = replacePatch('Large.java', 'old', 'é'.repeat(40000));
  const review = prepare(patch, { 'Large.java': 'old\n' });
  const plan = publisher.planReview(review, []);
  const complete = publisher.buildReviewBody(review, plan, { maxBytes: Infinity });
  const limited = publisher.buildReviewBody(review, plan, { artifactUrl: 'https://example.test/artifact', maxBytes: 1200 });
  assert.ok(Buffer.byteLength(complete) > 55000);
  assert.ok(Buffer.byteLength(limited) <= 1200);
  assert.match(limited, /truncat|exceeds|too large/i);
  assert.ok(limited.includes('https://example.test/artifact'));
  assert.ok(limited.includes(review.marker));
});

test('an oversized review without an uploaded artifact fails instead of hiding edits behind a run link', () => {
  const review = prepare(replacePatch('Large.java', 'old', 'x'.repeat(60000)), { 'Large.java': 'old\n' });
  assert.throws(() => publisher.buildReviewBody(review, publisher.planReview(review, []), {
    runUrl: 'https://github.com/owner/repo/actions/runs/123',
  }), /artifact/i);
});

function mockGithub({ head = HEAD, prFiles = [fullPrFile()], reviews = [], createError } = {}) {
  const calls = [];
  const listFiles = () => {};
  const listReviews = () => {};
  let creates = 0;
  return {
    calls,
    rest: {
      pulls: {
        listFiles,
        listReviews,
        get: async () => { calls.push(['get']); return { data: { head: { sha: head } } }; },
        createReview: async (request) => {
          calls.push(['createReview', request]);
          creates += 1;
          if (createError && creates === 1) throw createError;
          return { data: { id: 5, html_url: 'https://example.test/review' } };
        },
      },
    },
    paginate: async (method, options) => {
      assert.equal(options.per_page, 100);
      if (method === listFiles) { calls.push(['listFiles']); return prFiles; }
      if (method === listReviews) { calls.push(['listReviews']); return reviews; }
      assert.fail('Unexpected pagination method');
    },
  };
}

async function publishFixture(t, github, review = prepare()) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'cleanup-publisher-test-'));
  t.after(() => fs.rmSync(directory, { recursive: true, force: true }));
  fs.writeFileSync(path.join(directory, 'review.json'), JSON.stringify(review));
  const env = {
    REVIEW_OUTPUT_DIR: directory,
    REVIEW_HEAD_SHA: HEAD,
    REVIEW_TOOL_NAME: 'encoding-cleanup',
    REVIEW_SUGGESTION_SCOPE: review.scope,
    REVIEW_ARTIFACT_URL: 'https://example.test/artifact',
  };
  return publisher.publish({
    github,
    context: { repo: { owner: 'owner', repo: 'repo' }, payload: { pull_request: { number: 42 } }, runId: 123, serverUrl: 'https://github.com' },
    core: { info() {}, warning() {} },
    env,
  });
}

test('publisher paginates files and reviews, then checks the exact head immediately before posting', async (t) => {
  const github = mockGithub();
  await publishFixture(t, github);
  const mutationIndex = github.calls.findIndex(([name]) => name === 'createReview');
  assert.equal(github.calls[mutationIndex - 1][0], 'get');
  const request = github.calls[mutationIndex][1];
  assert.equal(request.commit_id, HEAD);
  assert.equal(request.event, 'COMMENT');
  assert.equal(request.comments.length, 1);
  assert.ok(request.body.includes('+' + IMPORT));
  assert.ok(request.body.includes('+' + CALL));
  assert.ok(request.body.includes('https://example.test/artifact'));
});

test('a stale PR head prevents every publication', async (t) => {
  const github = mockGithub({ head: 'b'.repeat(40) });
  await assert.rejects(publishFixture(t, github), /head|stale/i);
  assert.equal(github.calls.filter(([name]) => name === 'createReview').length, 0);
});

test('an existing review cannot make a stale-head retry succeed', async (t) => {
  const review = prepare();
  const github = mockGithub({ head: 'b'.repeat(40), reviews: [
    { body: review.marker, user: { login: 'github-actions[bot]', type: 'Bot' }, state: 'COMMENTED' },
  ] });
  await assert.rejects(publishFixture(t, github, review), /head|stale/i);
  assert.equal(github.calls.filter(([name]) => name === 'createReview').length, 0);
});

test('retry deduplication requires the marker from the same GitHub Actions bot', async (t) => {
  const review = prepare();
  const duplicate = mockGithub({ reviews: [{ body: review.marker, user: { login: 'github-actions[bot]', type: 'Bot' }, state: 'COMMENTED' }] });
  await publishFixture(t, duplicate, review);
  assert.equal(duplicate.calls.filter(([name]) => name === 'createReview').length, 0);
  for (const user of [{ login: 'someone', type: 'User' }, { login: 'another[bot]', type: 'Bot' }]) {
    const github = mockGithub({ reviews: [{ body: review.marker, user, state: 'COMMENTED' }] });
    await publishFixture(t, github, review);
    assert.equal(github.calls.filter(([name]) => name === 'createReview').length, 1);
  }
});

test('422 inline rejection has one body-only fallback retaining the complete grouped patch', async (t) => {
  const github = mockGithub({ createError: Object.assign(new Error('Invalid review range'), { status: 422 }) });
  await publishFixture(t, github);
  const mutations = github.calls.filter(([name]) => name === 'createReview');
  assert.equal(mutations.length, 2);
  assert.equal(mutations[0][1].comments.length, 1);
  assert.ok(!mutations[1][1].comments || mutations[1][1].comments.length === 0);
  assert.ok(mutations[1][1].body.includes('+' + IMPORT));
  assert.ok(mutations[1][1].body.includes('+' + CALL));
  assert.ok(mutations[1][1].body.includes('https://example.test/artifact'));
  assert.match(mutations[1][1].body, /GitHub.*reject|inline.*unavailable/i);
  assert.ok(!mutations[1][1].body.includes('One native suggestion contains'));
  const retryIndex = github.calls.lastIndexOf(mutations[1]);
  assert.equal(github.calls[retryIndex - 1][0], 'get');
});

test('non-422 publication failures propagate without a mutation retry', async (t) => {
  const github = mockGithub({ createError: Object.assign(new Error('Not permitted'), { status: 403 }) });
  await assert.rejects(publishFixture(t, github), /Not permitted/);
  assert.equal(github.calls.filter(([name]) => name === 'createReview').length, 1);
});

test('prepare CLI saves complete review files without changing the worktree or index', (t) => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'cleanup-prepare-test-'));
  t.after(() => fs.rmSync(directory, { recursive: true, force: true }));
  const repo = path.join(directory, 'repo');
  const output = path.join(directory, 'output');
  fs.mkdirSync(repo);
  fs.mkdirSync(output);
  const git = (...args) => execFileSync('git', args, { cwd: repo });
  git('init', '-q');
  git('config', 'user.name', 'Test');
  git('config', 'user.email', 'test@example.invalid');
  fs.writeFileSync(path.join(repo, 'A.java'), 'old\n');
  git('add', 'A.java');
  git('commit', '-qm', 'before');
  const head = git('rev-parse', 'HEAD').toString().trim();
  fs.writeFileSync(path.join(repo, 'A.java'), 'new\n');
  const patch = git('diff', '--binary');
  const patchFile = path.join(output, 'suggestions.patch');
  fs.writeFileSync(patchFile, patch);
  const statusBefore = git('status', '--porcelain=v1', '-z');
  execFileSync(process.execPath, [path.resolve(__dirname, '../cleanup-review.cjs'), '--prepare'], {
    cwd: repo,
    env: { ...process.env, REVIEW_HEAD_SHA: head, REVIEW_PATCH_FILE: patchFile, REVIEW_OUTPUT_DIR: output, REVIEW_TOOL_NAME: 'encoding-cleanup', REVIEW_SUGGESTION_SCOPE: 'run' },
  });
  assert.deepEqual(git('status', '--porcelain=v1', '-z'), statusBefore);
  assert.deepEqual(git('diff', '--binary'), patch);
  const review = JSON.parse(fs.readFileSync(path.join(output, 'review.json'), 'utf8'));
  assert.equal(review.headSha, head);
  assert.ok(fs.readFileSync(path.join(output, 'review.md'), 'utf8').includes(patch.toString().trimEnd()));
});
