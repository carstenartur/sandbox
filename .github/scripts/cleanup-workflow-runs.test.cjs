'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');

const {
  RATE_LIMIT_RESERVE,
  computeDeletionBudget,
  createdRange,
  pageRuns,
  parseBoolean,
  parseInteger,
  selectDeletionCandidates,
} = require('./cleanup-workflow-runs.cjs');

function run(id, workflowId, createdAt, workflowName = `workflow-${workflowId}`) {
  return { id, workflowId, workflowName, createdAt, conclusion: 'success' };
}

test('selectDeletionCandidates keeps the newest configured old runs per workflow', () => {
  const runs = [
    run(1, 10, '2026-01-01T00:00:00Z'),
    run(2, 10, '2026-01-02T00:00:00Z'),
    run(3, 10, '2026-01-03T00:00:00Z'),
    run(4, 20, '2026-01-01T00:00:00Z'),
    run(5, 20, '2026-01-04T00:00:00Z'),
  ];

  const result = selectDeletionCandidates(runs, 1);
  assert.equal(result.protectedCount, 2);
  assert.equal(result.workflowCount, 2);
  assert.deepEqual(result.candidates.map((candidate) => candidate.id), [1, 4, 2]);
});

test('selection is oldest-first and honours explicit protected run IDs', () => {
  const runs = [
    run(9, 1, '2026-01-03T00:00:00Z'),
    run(7, 1, '2026-01-01T00:00:00Z'),
    run(8, 1, '2026-01-02T00:00:00Z'),
  ];

  const result = selectDeletionCandidates(runs, 0, [8]);
  assert.equal(result.protectedCount, 1);
  assert.deepEqual(result.candidates.map((candidate) => candidate.id), [7, 9]);
});

test('pageRuns accepts both Octokit pagination response shapes', () => {
  const runs = [{ id: 1 }];
  assert.deepEqual(pageRuns({ data: runs }), runs);
  assert.deepEqual(pageRuns({ data: { workflow_runs: runs } }), runs);
  assert.throws(() => pageRuns({ data: {} }), /Unexpected workflow-run pagination response shape/);
});

test('computeDeletionBudget leaves a safety reserve for API reporting', () => {
  assert.equal(computeDeletionBudget(5000, 4000, false), 4000);
  assert.equal(computeDeletionBudget(RATE_LIMIT_RESERVE + 17, 4000, false), 17);
  assert.equal(computeDeletionBudget(100, 4000, false), 0);
  assert.equal(computeDeletionBudget(100, 4000, true), 4000);
});

test('createdRange produces an inclusive second-precision GitHub range', () => {
  assert.equal(createdRange(0, 1), '1970-01-01T00:00:00Z..1970-01-01T00:00:01Z');
  assert.throws(() => createdRange(2, 1), /Invalid created range/);
});

test('integer inputs accept only whole numeric values', () => {
  assert.equal(parseInteger('30', 'days', 1, 1, 100), 30);
  assert.equal(parseInteger('30.0', 'days', 1, 1, 100), 30);
  assert.equal(parseInteger('', 'days', 7, 1, 100), 7);
  assert.throws(() => parseInteger('30days', 'days', 7, 1, 100), /whole number/);
  assert.throws(() => parseInteger('30.5', 'days', 7, 1, 100), /whole number/);
  assert.throws(() => parseInteger('-1', 'days', 7, 1, 100), /whole number/);
  assert.throws(() => parseInteger('0', 'days', 7, 1, 100), /between 1 and 100/);
});

test('boolean inputs reject ambiguous values', () => {
  assert.equal(parseBoolean(true, 'dry'), true);
  assert.equal(parseBoolean('false', 'dry'), false);
  assert.equal(parseBoolean('', 'dry', false), false);
  assert.throws(() => parseBoolean('yes', 'dry'), /true or false/);
});
