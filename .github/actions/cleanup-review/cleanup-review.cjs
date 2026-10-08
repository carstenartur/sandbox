// Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { captureProposal, ensureProposal } = require('./cleanup-proposal.cjs');
const MAX_BODY_BYTES = 55000;
const MAX_PREVIEW_FILES = 10;
const TICK = String.fromCharCode(96);
const digest = (value) => crypto.createHash('sha256').update(value).digest('hex');

function fenceFor(text, minimum = 3) {
  let length = minimum;
  for (const match of text.matchAll(new RegExp(TICK + '+', 'g'))) length = Math.max(length, match[0].length + 1);
  return TICK.repeat(length);
}

function codeFence(text, language) {
  const fence = fenceFor(text);
  return fence + language + '\n' + text + (text.endsWith('\n') ? '' : '\n') + fence;
}

function inlineCode(text) {
  const value = String(text).replace(/[\r\n\t]/g, (c) => ({ '\r': '\\r', '\n': '\\n', '\t': '\\t' })[c]);
  const fence = fenceFor(value, 1);
  return fence + ' ' + value + ' ' + fence;
}

function safeUrl(value) {
  if (!value) return '';
  const url = new URL(value);
  if (!['https:', 'http:'].includes(url.protocol)) throw new Error('Review links must use HTTP or HTTPS');
  return url.href.replace(/>/g, '%3E').replace(/</g, '%3C');
}

function markerFor(review, proposal) {
  return '<!-- sandbox-cleanup-review:v2:' + review.headSha + ':' + review.patchSha256 + ':'
    + digest(review.toolName).slice(0, 16) + ':' + (proposal?.commitSha || 'patch') + ' -->';
}

function buildReviewBody(review, { proposal, repoUrl = '', pullNumber, headBranch = '', artifactUrl = '', runUrl = '', pending = false, reason = '', maxBytes = MAX_BODY_BYTES } = {}) {
  const artifact = safeUrl(artifactUrl), run = safeUrl(runUrl);
  const header = [
    '## Cleanup changes',
    inlineCode(review.toolName) + ' changed **' + review.files.length + ' ' + (review.files.length === 1 ? 'file' : 'files') + '**.',
  ];
  if (proposal) {
    const repository = safeUrl(repoUrl).replace(/\/$/, '');
    const compare = repository + '/compare/' + review.headSha + '...' + proposal.commitSha;
    const query = new URLSearchParams({
      quick_pull: '1',
      title: review.toolName + ' cleanup for #' + pullNumber,
      body: 'Apply the complete cleanup result to #' + pullNumber + '.\n\nAnalyzed head: ' + review.headSha
        + '\nCleanup commit: ' + proposal.commitSha + '\n\nMerge into ' + headBranch
        + ' to update the original PR. If that branch has advanced, use its newest cleanup proposal.',
    });
    const open = repository + '/compare/' + encodeURIComponent(headBranch) + '...' + encodeURIComponent(proposal.branch) + '?' + query;
    header.push(
      '**[Review cleanup diff](<' + compare + '>) · [Open cleanup PR](<' + open + '>)**',
      'The diff shows the actual changed locations with normal context. All files are kept together in one commit.',
      'To apply the changes, open the cleanup PR and merge it into ' + inlineCode(headBranch)
        + '. This updates #' + pullNumber + ' for its normal PR checks.',
    );
  } else {
    header.push(pending ? 'Complete captured cleanup result. Proposal links are added when the review is published.'
      : '**Cleanup PR unavailable.** ' + (reason || 'Use the complete patch below.'));
  }
  if (artifact) header.push('[Download complete patch and reports](<' + artifact + '>)');
  const prefix = header.join('\n\n') + '\n\n';
  const suffix = '\n\n<details>\n<summary>Analysis details</summary>\n\nAnalyzed head: ' + inlineCode(review.headSha)
    + '. Use the newest cleanup proposal if the original branch has advanced.\n\nPatch SHA-256: '
    + inlineCode(review.patchSha256) + '.' + (run ? '\n\n[Workflow run](<' + run + '>)' : '')
    + '\n\n</details>\n\n' + markerFor(review, proposal) + '\n';

  let contents = '';
  if (proposal) {
    contents = '| File | Added | Removed |\n|---|---:|---:|\n';
    let shown = 0;
    for (const file of review.files.slice(0, MAX_PREVIEW_FILES)) {
      const row = '| ' + inlineCode(file.path).replace(/\|/g, '\\|') + ' | '
        + (file.additions ?? 'binary') + ' | ' + (file.deletions ?? 'binary') + ' |\n';
      const reserve = '\n' + review.files.length + ' additional files are available in the complete comparison.';
      if (Buffer.byteLength(prefix + contents + row + reserve + suffix) > maxBytes) break;
      contents += row;
      shown += 1;
    }
    if (shown < review.files.length) contents += '\n' + (review.files.length - shown) + ' additional files are available in the complete comparison.';
  } else {
    contents = review.files.map((file) => '### ' + inlineCode(file.path) + '\n\n'
      + codeFence(file.diff, file.encoding === 'base64' ? 'base64' : 'diff')).join('\n\n');
  }
  let body = prefix + contents + suffix;
  if (Buffer.byteLength(body) > maxBytes && !proposal) {
    if (!artifact) throw new Error('The complete review exceeds the comment limit and no artifact is available');
    body = prefix + 'The complete diff exceeds the comment limit. Every change is retained in the complete artifact.' + suffix;
  }
  if (Buffer.byteLength(body) > maxBytes) throw new Error('The review metadata exceeds the comment limit');
  return body;
}

function required(env, name) {
  if (!env[name]) throw new Error(name + ' is required');
  return env[name];
}

function prepareFromEnvironment(env = process.env) {
  const headSha = required(env, 'REVIEW_HEAD_SHA');
  const outputDir = required(env, 'REVIEW_OUTPUT_DIR');
  const patch = fs.readFileSync(required(env, 'REVIEW_PATCH_FILE'));
  const review = {
    ...captureProposal({ headSha, patch }),
    schemaVersion: 2, toolName: env.REVIEW_TOOL_NAME || 'sandbox-cleanup', patchBase64: patch.toString('base64'),
  };
  fs.mkdirSync(outputDir, { recursive: true });
  fs.writeFileSync(path.join(outputDir, 'review.json'), JSON.stringify(review, null, 2) + '\n');
  fs.writeFileSync(path.join(outputDir, 'review.md'), buildReviewBody(review, { pending: true, maxBytes: Infinity }));
  return review;
}

async function publish({ github, context, core, env = process.env }) {
  const review = JSON.parse(fs.readFileSync(path.join(required(env, 'REVIEW_OUTPUT_DIR'), 'review.json'), 'utf8'));
  const headSha = required(env, 'REVIEW_HEAD_SHA');
  if (review.schemaVersion !== 2 || review.headSha !== headSha || review.toolName !== (env.REVIEW_TOOL_NAME || 'sandbox-cleanup')) {
    throw new Error('Prepared review metadata does not match the requested head or tool');
  }
  const patch = Buffer.from(review.patchBase64, 'base64');
  if (patch.toString('base64') !== review.patchBase64 || digest(patch) !== review.patchSha256) {
    throw new Error('Prepared cleanup patch checksum does not match');
  }
  const pullNumber = context.payload?.pull_request?.number || context.issue?.number;
  if (!Number.isSafeInteger(pullNumber) || pullNumber < 1) throw new Error('A pull request number is required');
  const repo = context.repo, parameters = { ...repo, pull_number: pullNumber };
  const assertCurrentHead = async () => {
    const { data: pr } = await github.rest.pulls.get(parameters);
    if (pr.state !== 'open') throw new Error('Refusing cleanup publication for a closed pull request');
    if (pr.head.repo?.full_name?.toLowerCase() !== (repo.owner + '/' + repo.repo).toLowerCase()) {
      throw new Error('Cleanup proposals require the pull request branch in the same repository');
    }
    if (pr.head.sha !== headSha) throw new Error('Refusing stale cleanup review: the PR head has changed');
    if (!pr.head.ref || pr.head.ref.startsWith('cleanup/pr-')) throw new Error('Cleanup proposal branches cannot create further proposals');
    return pr;
  };
  const [, reviews] = await Promise.all([
    assertCurrentHead(),
    github.paginate(github.rest.pulls.listReviews, { ...parameters, per_page: 100 }),
  ]);
  let proposal, reason = '';
  try {
    proposal = await ensureProposal({ github, repo, review, pullNumber, assertCurrentHead });
  } catch (error) {
    if (error.status !== 403) throw error;
    reason = 'GitHub refused to create the cleanup branch. The complete patch remains available.';
    core.warning(reason);
  }
  const pr = await assertCurrentHead();
  const marker = markerFor(review, proposal);
  if (reviews.some((item) => item.user?.type === 'Bot' && item.user?.login === 'github-actions[bot]'
      && item.state !== 'PENDING' && item.body?.includes(marker))) {
    core.info('The GitHub Actions bot already published this exact cleanup result');
    return { duplicate: true, proposal };
  }
  const server = (context.serverUrl || env.GITHUB_SERVER_URL || 'https://github.com').replace(/\/$/, '');
  const repoUrl = server + '/' + encodeURIComponent(repo.owner) + '/' + encodeURIComponent(repo.repo);
  const runId = context.runId || env.GITHUB_RUN_ID;
  const body = buildReviewBody(review, {
    proposal, repoUrl, pullNumber, headBranch: pr.head.ref, artifactUrl: env.REVIEW_ARTIFACT_URL || '',
    runUrl: runId ? repoUrl + '/actions/runs/' + runId : '', reason,
  });
  const result = await github.rest.pulls.createReview({ ...parameters, commit_id: headSha, event: 'COMMENT', body });
  core.info(proposal ? 'Published complete cleanup proposal at ' + proposal.commitSha : 'Published the complete cleanup patch fallback');
  return { review: result.data, proposal };
}

module.exports = { prepareFromEnvironment, buildReviewBody, publish };
if (require.main === module) {
  try {
    if (process.argv.length !== 3 || process.argv[2] !== '--prepare') throw new Error('Usage: node cleanup-review.cjs --prepare');
    const review = prepareFromEnvironment();
    process.stdout.write('Prepared complete cleanup proposal for ' + review.files.length + ' file(s) at ' + review.headSha + '\n');
  } catch (error) {
    process.stderr.write(error.message + '\n');
    process.exitCode = 1;
  }
}
