// Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const { execFileSync } = require('node:child_process');
const { test } = require('node:test');
const publisher = require('../cleanup-review.cjs');

const HEAD = 'a'.repeat(40), TREE = 'b'.repeat(40), RESULT = 'c'.repeat(40), COMMIT = 'd'.repeat(40);
const PATCH = 'diff --git a/A.java b/A.java\n--- a/A.java\n+++ b/A.java\n@@ -1 +1 @@\n-old\n+new\n';
const digest = (value) => crypto.createHash('sha256').update(value).digest('hex');
function review() {
  const content = Buffer.from('new\n');
  return {
    schemaVersion: 2, headSha: HEAD, baseTreeSha: TREE, cleanedTreeSha: RESULT, toolName: 'encoding-cleanup',
    patchBase64: Buffer.from(PATCH).toString('base64'), patchSha256: digest(PATCH),
    changes: [{ path: 'A.java', mode: '100644', blobSha: crypto.createHash('sha1').update('blob 4\0').update(content).digest('hex'), contentBase64: content.toString('base64') }],
    files: [{ path: 'A.java', diff: PATCH, additions: 1, deletions: 1 }],
  };
}
const proposal = { branch: 'cleanup/pr-42/result', commitSha: COMMIT };
const options = { proposal, repoUrl: 'https://github.com/owner/repo', pullNumber: 42, headBranch: 'feature/ä+detail' };

test('even a huge cleanup has a compact review linking the exact normal Git diff, with no inline replacements', () => {
  const prepared = review();
  prepared.files[0].diff += '+a real change\n'.repeat(10000);
  const body = publisher.buildReviewBody(prepared, { ...options, comments: [], decisions: [] });
  assert.ok(Buffer.byteLength(body) < 4000);
  assert.ok(body.includes('/compare/' + HEAD + '...' + COMMIT));
  assert.ok(!body.includes('a real change'));
  assert.ok(!body.includes('suggestion\n'));
  assert.ok(!body.includes('Commit suggestion'));
});

test('the cleanup PR form targets the original feature branch and does not claim a click applies changes', () => {
  const body = publisher.buildReviewBody(review(), options);
  const link = body.match(/\[Open cleanup PR\]\(<([^>]+)>\)/);
  assert.ok(link, body);
  const url = new URL(link[1]);
  assert.equal(decodeURIComponent(url.pathname), '/owner/repo/compare/feature/ä+detail...cleanup/pr-42/result');
  assert.equal(url.searchParams.get('quick_pull'), '1');
  assert.ok(url.searchParams.get('body').includes(HEAD));
  assert.match(body, /merge.*feature\/ä\+detail/i);
  assert.ok(!url.pathname.includes('/compare/main...'));
});

test('large file listings remain bounded while the commit still supplies the complete result', () => {
  const prepared = review();
  prepared.files = Array.from({ length: 500 }, (_, i) => ({ path: 'src/' + 'long/'.repeat(25) + i + '.java', additions: 1, deletions: 1 }));
  const body = publisher.buildReviewBody(prepared, { ...options, maxBytes: 3000 });
  assert.ok(Buffer.byteLength(body) <= 3000);
  assert.ok(body.includes('500'));
  assert.match(body, /remaining|additional/i);
  assert.ok(body.includes('/compare/' + HEAD + '...' + COMMIT));
});

test('the prepared artifact retains every diff without making native suggestions', () => {
  const prepared = review();
  prepared.files.push({ path: 'Other.java', diff: 'diff --git a/Other.java b/Other.java\n+companion\n', additions: 1, deletions: 0 });
  const body = publisher.buildReviewBody(prepared, { pending: true, maxBytes: Infinity });
  for (const file of prepared.files) assert.ok(body.includes(file.diff.trimEnd()));
  assert.ok(!body.includes('suggestion\n'));
});

test('a missing proposal retains the complete patch and never truncates it without an artifact', () => {
  const prepared = review();
  let body = publisher.buildReviewBody(prepared, { reason: 'GitHub refused the cleanup branch' });
  assert.ok(body.includes(PATCH.trimEnd()));
  assert.match(body, /refused/);
  prepared.files[0].diff += '+é\n'.repeat(30000);
  assert.throws(() => publisher.buildReviewBody(prepared), /artifact|complete/i);
  body = publisher.buildReviewBody(prepared, { artifactUrl: 'https://example.test/artifact' });
  assert.ok(Buffer.byteLength(body) <= 55000);
  assert.ok(body.includes('https://example.test/artifact'));
  assert.match(body, /complete.*artifact|artifact.*complete/i);
});

function mockGithub({ head = HEAD, state = 'open', foreign = false, denyProposal = false, failReview = false } = {}) {
  const calls = [], reviews = [];
  let ref;
  const api = {
    calls, reviews, head, denyProposal, failReview,
    rest: {
      pulls: {
        listReviews() {},
        get: async () => {
          calls.push(['getPR']);
          return { data: { state, head: { sha: api.head, ref: options.headBranch, repo: { full_name: foreign ? 'other/repo' : 'owner/repo' } } } };
        },
        createReview: async (request) => {
          calls.push(['createReview', request]);
          if (api.failReview) throw Object.assign(new Error('Review service unavailable'), { status: 503 });
          reviews.push({ body: request.body, state: 'COMMENTED', user: { login: 'github-actions[bot]', type: 'Bot' } });
          return { data: { id: reviews.length } };
        },
      },
      git: {
        getRef: async () => {
          calls.push(['getRef']);
          if (!ref) throw Object.assign(new Error('Not found'), { status: 404 });
          return { data: { object: { sha: ref, type: 'commit' } } };
        },
        getCommit: async () => ({ data: { sha: COMMIT, tree: { sha: RESULT }, parents: [{ sha: HEAD }] } }),
        createBlob: async (request) => {
          calls.push(['createBlob', request]);
          if (api.denyProposal) throw Object.assign(new Error('Resource not accessible'), { status: 403 });
          return { data: { sha: review().changes[0].blobSha } };
        },
        createTree: async (request) => {
          calls.push(['createTree', request]);
          if (api.denyProposal) throw Object.assign(new Error('Resource not accessible'), { status: 403 });
          return { data: { sha: RESULT } };
        },
        createCommit: async (request) => { calls.push(['createCommit', request]); return { data: { sha: COMMIT } }; },
        createRef: async (request) => { calls.push(['createRef', request]); ref = request.sha; return { data: { object: { sha: ref } } }; },
      },
    },
    paginate: async (method) => {
      assert.equal(method, api.rest.pulls.listReviews, 'No PR-diff filtering or independently selectable inline comments');
      return reviews;
    },
  };
  return api;
}

async function publishFixture(t, github, prepared = review(), overrides = {}) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'cleanup-review-test-'));
  t.after(() => fs.rmSync(directory, { recursive: true, force: true }));
  fs.writeFileSync(path.join(directory, 'review.json'), JSON.stringify(prepared));
  return publisher.publish({
    github, context: { repo: { owner: 'owner', repo: 'repo' }, payload: { pull_request: { number: 42 } }, runId: 123 },
    core: { info() {}, warning() {} },
    env: { REVIEW_OUTPUT_DIR: directory, REVIEW_HEAD_SHA: HEAD, REVIEW_TOOL_NAME: 'encoding-cleanup', REVIEW_ARTIFACT_URL: 'https://example.test/artifact', ...overrides },
  });
}

test('publication keeps every edit in a separate commit, checks the exact head, and posts only compact review links', async (t) => {
  const github = mockGithub();
  await publishFixture(t, github);
  const commit = github.calls.find(([name]) => name === 'createCommit')[1];
  assert.deepEqual(commit.parents, [HEAD]);
  assert.equal(commit.tree, RESULT);
  const refIndex = github.calls.findIndex(([name]) => name === 'createRef');
  assert.equal(github.calls[refIndex - 1][0], 'getPR');
  assert.ok(github.calls[refIndex][1].ref.startsWith('refs/heads/cleanup/pr-42/'));
  const postIndex = github.calls.findIndex(([name]) => name === 'createReview');
  assert.equal(github.calls[postIndex - 1][0], 'getPR');
  const request = github.calls[postIndex][1];
  assert.equal(request.commit_id, HEAD);
  assert.equal(request.comments, undefined);
  assert.ok(request.body.includes('/compare/' + HEAD + '...' + COMMIT));
});

test('stale, closed, and foreign PRs are rejected before creating any Git objects', async (t) => {
  for (const configuration of [{ head: 'e'.repeat(40) }, { state: 'closed' }, { foreign: true }]) {
    const github = mockGithub(configuration);
    await assert.rejects(publishFixture(t, github), /head|stale|closed|repository/i);
    assert.equal(github.calls.filter(([name]) => name.startsWith('create')).length, 0);
  }
});

test('a retry reuses the verified proposal and same-bot review but still rejects a stale head', async (t) => {
  const github = mockGithub();
  await publishFixture(t, github);
  const duplicate = await publishFixture(t, github);
  assert.equal(duplicate.duplicate, true);
  assert.equal(github.calls.filter(([name]) => name === 'createCommit').length, 1);
  assert.equal(github.reviews.length, 1);
  github.head = 'e'.repeat(40);
  await assert.rejects(publishFixture(t, github), /head|stale/i);
});

test('copied review markers from another author do not suppress publication', async (t) => {
  const github = mockGithub();
  await publishFixture(t, github);
  github.reviews[0].user = { login: 'someone', type: 'User' };
  await publishFixture(t, github);
  assert.equal(github.reviews.length, 2);
});

test('a permissions fallback retains the patch and a later authorized retry can publish the proposal', async (t) => {
  const github = mockGithub({ denyProposal: true });
  await publishFixture(t, github);
  assert.ok(github.reviews[0].body.includes(PATCH.trimEnd()));
  assert.ok(!github.reviews[0].body.includes('[Open cleanup PR]'));
  github.denyProposal = false;
  await publishFixture(t, github);
  assert.equal(github.reviews.length, 2);
  assert.ok(github.reviews[1].body.includes('[Open cleanup PR]'));
});

test('a failed review post can retry without creating another cleanup commit', async (t) => {
  const github = mockGithub({ failReview: true });
  await assert.rejects(publishFixture(t, github), /unavailable/i);
  github.failReview = false;
  await publishFixture(t, github);
  assert.equal(github.calls.filter(([name]) => name === 'createCommit').length, 1);
  assert.equal(github.reviews.length, 1);
});

test('changed evidence metadata or patch bytes fail before any network mutation', async (t) => {
  for (const corrupt of [{ schemaVersion: 1 }, { headSha: 'f'.repeat(40) }, { toolName: 'different' }, { patchBase64: Buffer.from('different').toString('base64') }]) {
    const github = mockGithub();
    await assert.rejects(publishFixture(t, github, { ...review(), ...corrupt }), /metadata|checksum/i);
    assert.equal(github.calls.length, 0);
  }
});

test('prepare CLI writes the Git manifest and full artifact review while preserving the cleaned checkout', (t) => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'cleanup-cli-test-'));
  t.after(() => fs.rmSync(directory, { recursive: true, force: true }));
  const repo = path.join(directory, 'repo'), output = path.join(directory, 'output');
  fs.mkdirSync(repo); fs.mkdirSync(output);
  const git = (...args) => execFileSync('git', args, { cwd: repo });
  git('init', '-q'); git('config', 'user.name', 'Test'); git('config', 'user.email', 'test@example.invalid');
  fs.writeFileSync(path.join(repo, 'A.java'), 'old\n');
  git('add', '.'); git('commit', '-qm', 'before');
  const head = git('rev-parse', 'HEAD').toString().trim();
  fs.writeFileSync(path.join(repo, 'A.java'), 'new\n');
  const patchFile = path.join(output, 'suggestions.patch');
  fs.writeFileSync(patchFile, git('diff', '--binary'));
  const status = git('status', '--porcelain=v1', '-z');
  execFileSync(process.execPath, [path.resolve(__dirname, '../cleanup-review.cjs'), '--prepare'], {
    cwd: repo, env: { ...process.env, REVIEW_HEAD_SHA: head, REVIEW_PATCH_FILE: patchFile, REVIEW_OUTPUT_DIR: output },
  });
  const prepared = JSON.parse(fs.readFileSync(path.join(output, 'review.json'), 'utf8'));
  assert.equal(prepared.headSha, head);
  assert.equal(prepared.schemaVersion, 2);
  assert.equal(prepared.changes.length, 1);
  assert.equal(Buffer.from(prepared.changes[0].contentBase64, 'base64').toString(), 'new\n');
  assert.deepEqual(git('status', '--porcelain=v1', '-z'), status);
  assert.ok(fs.readFileSync(path.join(output, 'review.md'), 'utf8').includes('+new'));
});
