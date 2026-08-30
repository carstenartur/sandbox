'use strict';

const MAX_FILTERED_RESULTS = 1000;
const SEARCH_START_SECONDS = Math.floor(Date.UTC(2008, 0, 1) / 1000);
const RATE_LIMIT_RESERVE = 350;

function parseInteger(value, name, fallback, minimum, maximum) {
  const raw = value === undefined || value === null || value === '' ? fallback : value;
  const parsed = Number.parseInt(String(raw), 10);
  if (!Number.isSafeInteger(parsed) || parsed < minimum || parsed > maximum) {
    throw new Error(`${name} must be an integer between ${minimum} and ${maximum}; got ${raw}`);
  }
  return parsed;
}

function parseBoolean(value, name, fallback = false) {
  const raw = value === undefined || value === null || value === '' ? String(fallback) : String(value);
  if (raw === 'true') {
    return true;
  }
  if (raw === 'false') {
    return false;
  }
  throw new Error(`${name} must be true or false; got ${raw}`);
}

function isoFromSeconds(seconds) {
  return new Date(seconds * 1000).toISOString().replace('.000Z', 'Z');
}

function createdRange(startSeconds, endSeconds) {
  if (!Number.isSafeInteger(startSeconds) || !Number.isSafeInteger(endSeconds) || startSeconds > endSeconds) {
    throw new Error(`Invalid created range: ${startSeconds}..${endSeconds}`);
  }
  return `${isoFromSeconds(startSeconds)}..${isoFromSeconds(endSeconds)}`;
}

function minimalRun(run) {
  return {
    id: run.id,
    workflowId: run.workflow_id,
    workflowName: run.name || run.path || String(run.workflow_id),
    createdAt: run.created_at,
    conclusion: run.conclusion || 'unknown',
    event: run.event || 'unknown',
    headBranch: run.head_branch || '',
  };
}

function selectDeletionCandidates(runs, keepMinimumRuns) {
  const byWorkflow = new Map();
  for (const run of runs) {
    const key = run.workflowId;
    const group = byWorkflow.get(key) || [];
    group.push(run);
    byWorkflow.set(key, group);
  }

  const protectedIds = new Set();
  for (const group of byWorkflow.values()) {
    group.sort((left, right) => {
      const byTime = Date.parse(right.createdAt) - Date.parse(left.createdAt);
      return byTime !== 0 ? byTime : right.id - left.id;
    });
    for (const run of group.slice(0, keepMinimumRuns)) {
      protectedIds.add(run.id);
    }
  }

  const candidates = runs
    .filter((run) => !protectedIds.has(run.id))
    .sort((left, right) => {
      const byTime = Date.parse(left.createdAt) - Date.parse(right.createdAt);
      return byTime !== 0 ? byTime : left.id - right.id;
    });

  return {
    candidates,
    protectedCount: protectedIds.size,
    workflowCount: byWorkflow.size,
  };
}

function computeDeletionBudget(rateRemaining, requestedMaximum, dryRun) {
  if (dryRun) {
    return requestedMaximum;
  }
  return Math.min(requestedMaximum, Math.max(0, rateRemaining - RATE_LIMIT_RESERVE));
}

function sleep(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

async function listWindow({ github, owner, repo, startSeconds, endSeconds }) {
  const created = createdRange(startSeconds, endSeconds);
  const probe = await github.rest.actions.listWorkflowRunsForRepo({
    owner,
    repo,
    status: 'completed',
    created,
    per_page: 1,
  });
  const total = probe.data.total_count;
  if (total === 0) {
    return [];
  }

  // GitHub caps filtered workflow-run searches at 1,000 returned results. Split
  // the time interval recursively until every interval can be paginated fully.
  if (total >= MAX_FILTERED_RESULTS) {
    if (startSeconds === endSeconds) {
      throw new Error(`More than ${MAX_FILTERED_RESULTS - 1} completed runs share ${created}`);
    }
    const midpoint = Math.floor((startSeconds + endSeconds) / 2);
    const left = await listWindow({
      github,
      owner,
      repo,
      startSeconds,
      endSeconds: midpoint,
    });
    const right = await listWindow({
      github,
      owner,
      repo,
      startSeconds: midpoint + 1,
      endSeconds,
    });
    return left.concat(right);
  }

  const runs = [];
  for await (const response of github.paginate.iterator(github.rest.actions.listWorkflowRunsForRepo, {
    owner,
    repo,
    status: 'completed',
    created,
    per_page: 100,
  })) {
    for (const run of response.data.workflow_runs) {
      runs.push(minimalRun(run));
    }
  }
  return runs;
}

async function listOldCompletedRuns({ github, owner, repo, cutoffSeconds, core }) {
  if (cutoffSeconds <= SEARCH_START_SECONDS) {
    return [];
  }
  core.info(`Scanning completed runs from ${isoFromSeconds(SEARCH_START_SECONDS)} through ${isoFromSeconds(cutoffSeconds - 1)}`);
  const runs = await listWindow({
    github,
    owner,
    repo,
    startSeconds: SEARCH_START_SECONDS,
    endSeconds: cutoffSeconds - 1,
  });

  const unique = new Map();
  for (const run of runs) {
    unique.set(run.id, run);
  }
  return [...unique.values()];
}

function isRetryable(error) {
  const status = error.status || error.response?.status;
  if ([500, 502, 503, 504].includes(status)) {
    return true;
  }
  if (status === 403 || status === 429) {
    const headers = error.response?.headers || {};
    return Boolean(headers['retry-after']) || String(headers['x-ratelimit-remaining']) === '0';
  }
  return false;
}

async function deleteRun({ github, owner, repo, run, core }) {
  for (let attempt = 1; attempt <= 4; attempt += 1) {
    try {
      await github.rest.actions.deleteWorkflowRun({ owner, repo, run_id: run.id });
      return 'deleted';
    } catch (error) {
      const status = error.status || error.response?.status;
      if (status === 404) {
        return 'already-gone';
      }
      if (!isRetryable(error) || attempt === 4) {
        throw error;
      }
      const retryAfter = Number.parseInt(error.response?.headers?.['retry-after'] || '', 10);
      const delay = Number.isFinite(retryAfter) ? retryAfter * 1000 : 1000 * (2 ** (attempt - 1));
      core.warning(`Delete of run ${run.id} was rate-limited or transiently failed; retrying in ${delay} ms`);
      await sleep(delay);
    }
  }
  throw new Error(`Unreachable delete retry state for run ${run.id}`);
}

async function deleteInParallel({ github, owner, repo, runs, parallelism, core }) {
  let cursor = 0;
  let deleted = 0;
  let alreadyGone = 0;
  const failures = [];
  const deletedByWorkflow = new Map();

  async function worker() {
    while (true) {
      const index = cursor;
      cursor += 1;
      if (index >= runs.length) {
        return;
      }
      const run = runs[index];
      try {
        const result = await deleteRun({ github, owner, repo, run, core });
        if (result === 'deleted') {
          deleted += 1;
          deletedByWorkflow.set(run.workflowName, (deletedByWorkflow.get(run.workflowName) || 0) + 1);
        } else {
          alreadyGone += 1;
        }
      } catch (error) {
        failures.push({ run, error });
        core.error(`Failed to delete run ${run.id} (${run.workflowName}): ${error.message}`);
      }
      const processed = deleted + alreadyGone + failures.length;
      if (processed % 100 === 0 || processed === runs.length) {
        core.info(`Cleanup progress: ${processed}/${runs.length} processed; ${deleted} deleted; ${failures.length} failed`);
      }
    }
  }

  await Promise.all(Array.from({ length: Math.min(parallelism, runs.length || 1) }, () => worker()));
  return { deleted, alreadyGone, failures, deletedByWorkflow };
}

async function writeSummary({
  core,
  cutoffIso,
  retainDays,
  keepMinimumRuns,
  requestedMaximum,
  deletionBudget,
  dryRun,
  oldRuns,
  selectedRuns,
  protectedCount,
  workflowCount,
  deleted,
  alreadyGone,
  failures,
  deletedByWorkflow,
}) {
  const rows = [...deletedByWorkflow.entries()]
    .sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0]))
    .slice(0, 30)
    .map(([name, count]) => [name, String(count)]);

  core.summary
    .addHeading(dryRun ? 'Workflow run cleanup — dry run' : 'Workflow run cleanup')
    .addTable([
      [{ data: 'Setting', header: true }, { data: 'Value', header: true }],
      ['Retention', `${retainDays} days`],
      ['Cutoff', cutoffIso],
      ['Minimum retained per workflow', String(keepMinimumRuns)],
      ['Requested deletion cap', String(requestedMaximum)],
      ['Rate-aware deletion budget', String(deletionBudget)],
      ['Completed old runs discovered', String(oldRuns)],
      ['Protected old runs', String(protectedCount)],
      ['Workflows represented', String(workflowCount)],
      ['Runs selected this execution', String(selectedRuns)],
      ['Deleted', String(deleted)],
      ['Already gone', String(alreadyGone)],
      ['Failures', String(failures)],
    ]);

  if (rows.length > 0) {
    core.summary.addHeading('Deleted runs by workflow', 3).addTable([
      [{ data: 'Workflow', header: true }, { data: 'Deleted', header: true }],
      ...rows,
    ]);
  }
  await core.summary.write();
}

async function run({ github, context, core, env = process.env }) {
  const retainDays = parseInteger(env.RETAIN_DAYS, 'RETAIN_DAYS', 30, 1, 3650);
  const keepMinimumRuns = parseInteger(env.KEEP_MINIMUM_RUNS, 'KEEP_MINIMUM_RUNS', 5, 0, 100);
  const requestedMaximum = parseInteger(env.MAX_DELETIONS, 'MAX_DELETIONS', 4000, 1, 4500);
  const parallelism = parseInteger(env.PARALLELISM, 'PARALLELISM', 4, 1, 12);
  const dryRun = parseBoolean(env.DRY_RUN, 'DRY_RUN', false);

  const nowMilliseconds = Date.now();
  const cutoffMilliseconds = nowMilliseconds - retainDays * 24 * 60 * 60 * 1000;
  const cutoffSeconds = Math.floor(cutoffMilliseconds / 1000);
  const cutoffIso = isoFromSeconds(cutoffSeconds);
  const { owner, repo } = context.repo;

  core.info(`Repository: ${owner}/${repo}`);
  core.info(`Retention cutoff: ${cutoffIso}; keep minimum: ${keepMinimumRuns}; requested cap: ${requestedMaximum}; parallelism: ${parallelism}; dry run: ${dryRun}`);

  const oldRuns = await listOldCompletedRuns({ github, owner, repo, cutoffSeconds, core });
  const { candidates, protectedCount, workflowCount } = selectDeletionCandidates(oldRuns, keepMinimumRuns);

  const rate = await github.rest.rateLimit.get();
  const rateRemaining = rate.data.resources.core.remaining;
  const deletionBudget = computeDeletionBudget(rateRemaining, requestedMaximum, dryRun);
  const selected = candidates.slice(0, deletionBudget);

  core.info(`Discovered ${oldRuns.length} old completed runs; ${protectedCount} protected; ${candidates.length} eligible; core API remaining: ${rateRemaining}; deleting up to ${selected.length}`);

  let result = {
    deleted: 0,
    alreadyGone: 0,
    failures: [],
    deletedByWorkflow: new Map(),
  };

  if (dryRun) {
    for (const run of selected.slice(0, 50)) {
      core.info(`[dry run] ${run.id}\t${run.createdAt}\t${run.workflowName}\t${run.conclusion}`);
    }
    if (selected.length > 50) {
      core.info(`[dry run] ${selected.length - 50} additional selected runs omitted from the log`);
    }
  } else if (selected.length > 0) {
    result = await deleteInParallel({ github, owner, repo, runs: selected, parallelism, core });
  }

  await writeSummary({
    core,
    cutoffIso,
    retainDays,
    keepMinimumRuns,
    requestedMaximum,
    deletionBudget,
    dryRun,
    oldRuns: oldRuns.length,
    selectedRuns: selected.length,
    protectedCount,
    workflowCount,
    deleted: result.deleted,
    alreadyGone: result.alreadyGone,
    failures: result.failures.length,
    deletedByWorkflow: result.deletedByWorkflow,
  });

  core.setOutput('deleted', result.deleted);
  core.setOutput('remaining', Math.max(0, candidates.length - result.deleted - result.alreadyGone));

  if (result.failures.length > 0) {
    core.setFailed(`${result.failures.length} workflow runs could not be deleted`);
  }
}

module.exports = {
  RATE_LIMIT_RESERVE,
  computeDeletionBudget,
  createdRange,
  isoFromSeconds,
  parseBoolean,
  parseInteger,
  run,
  selectDeletionCandidates,
};
