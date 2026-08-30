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
  if (raw === 'true' || raw === 'false') {
    return raw === 'true';
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

function selectDeletionCandidates(runs, keepMinimumRuns) {
  const byWorkflow = new Map();
  for (const run of runs) {
    const group = byWorkflow.get(run.workflowId) || [];
    group.push(run);
    byWorkflow.set(run.workflowId, group);
  }

  const protectedIds = new Set();
  for (const group of byWorkflow.values()) {
    group.sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt) || b.id - a.id);
    group.slice(0, keepMinimumRuns).forEach((run) => protectedIds.add(run.id));
  }

  return {
    candidates: runs
      .filter((run) => !protectedIds.has(run.id))
      .sort((a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt) || a.id - b.id),
    protectedCount: protectedIds.size,
    workflowCount: byWorkflow.size,
  };
}

function computeDeletionBudget(rateRemaining, requestedMaximum, dryRun) {
  return dryRun
    ? requestedMaximum
    : Math.min(requestedMaximum, Math.max(0, rateRemaining - RATE_LIMIT_RESERVE));
}

function pageRuns(response) {
  const runs = Array.isArray(response.data) ? response.data : response.data?.workflow_runs;
  if (!Array.isArray(runs)) {
    throw new Error('Unexpected workflow-run pagination response shape');
  }
  return runs;
}

function compactRun(run) {
  return {
    id: run.id,
    workflowId: run.workflow_id,
    workflowName: run.name || run.path || String(run.workflow_id),
    createdAt: run.created_at,
    conclusion: run.conclusion || 'unknown',
  };
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

  // Filtered workflow-run searches return at most 1,000 results. Recursively
  // split the date interval so every leaf can be paginated completely.
  if (total >= MAX_FILTERED_RESULTS) {
    if (startSeconds === endSeconds) {
      throw new Error(`At least ${MAX_FILTERED_RESULTS} runs share ${created}`);
    }
    const midpoint = Math.floor((startSeconds + endSeconds) / 2);
    return (await listWindow({ github, owner, repo, startSeconds, endSeconds: midpoint }))
      .concat(await listWindow({ github, owner, repo, startSeconds: midpoint + 1, endSeconds }));
  }

  const runs = [];
  for await (const response of github.paginate.iterator(github.rest.actions.listWorkflowRunsForRepo, {
    owner,
    repo,
    status: 'completed',
    created,
    per_page: 100,
  })) {
    pageRuns(response).forEach((run) => runs.push(compactRun(run)));
  }
  return runs;
}

async function listOldCompletedRuns({ github, owner, repo, cutoffSeconds, core }) {
  if (cutoffSeconds <= SEARCH_START_SECONDS) {
    return [];
  }
  core.info(`Scanning ${isoFromSeconds(SEARCH_START_SECONDS)} through ${isoFromSeconds(cutoffSeconds - 1)}`);
  const runs = await listWindow({
    github,
    owner,
    repo,
    startSeconds: SEARCH_START_SECONDS,
    endSeconds: cutoffSeconds - 1,
  });
  return [...new Map(runs.map((run) => [run.id, run])).values()];
}

function retryDelay(error, attempt) {
  const status = error.status || error.response?.status;
  if (![403, 429, 500, 502, 503, 504].includes(status)) {
    return null;
  }
  const headers = error.response?.headers || {};
  if ((status === 403 || status === 429)
      && !headers['retry-after']
      && String(headers['x-ratelimit-remaining']) !== '0') {
    return null;
  }
  const retryAfter = Number.parseInt(headers['retry-after'] || '', 10);
  return Number.isFinite(retryAfter) ? retryAfter * 1000 : 1000 * (2 ** (attempt - 1));
}

async function deleteRun({ github, owner, repo, run, core }) {
  for (let attempt = 1; attempt <= 4; attempt += 1) {
    try {
      await github.rest.actions.deleteWorkflowRun({ owner, repo, run_id: run.id });
      return 'deleted';
    } catch (error) {
      if ((error.status || error.response?.status) === 404) {
        return 'already-gone';
      }
      const delay = retryDelay(error, attempt);
      if (delay === null || attempt === 4) {
        throw error;
      }
      core.warning(`Retrying deletion of run ${run.id} in ${delay} ms`);
      await new Promise((resolve) => setTimeout(resolve, delay));
    }
  }
  throw new Error(`Unreachable retry state for run ${run.id}`);
}

async function deleteInParallel({ github, owner, repo, runs, parallelism, core }) {
  let cursor = 0;
  let deleted = 0;
  let alreadyGone = 0;
  const failures = [];
  const deletedByWorkflow = new Map();

  async function worker() {
    while (cursor < runs.length) {
      const run = runs[cursor++];
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
        core.error(`Failed to delete ${run.id} (${run.workflowName}): ${error.message}`);
      }
      const processed = deleted + alreadyGone + failures.length;
      if (processed % 100 === 0 || processed === runs.length) {
        core.info(`Progress ${processed}/${runs.length}: ${deleted} deleted, ${failures.length} failed`);
      }
    }
  }

  await Promise.all(Array.from({ length: Math.min(parallelism, Math.max(1, runs.length)) }, worker));
  return { deleted, alreadyGone, failures, deletedByWorkflow };
}

async function summarize(core, data) {
  const workflowRows = [...data.deletedByWorkflow.entries()]
    .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
    .slice(0, 30)
    .map(([name, count]) => [name, String(count)]);

  core.summary
    .addHeading(data.dryRun ? 'Workflow run cleanup — dry run' : 'Workflow run cleanup')
    .addTable([
      [{ data: 'Metric', header: true }, { data: 'Value', header: true }],
      ['Retention', `${data.retainDays} days`],
      ['Cutoff', data.cutoffIso],
      ['Old completed runs discovered', String(data.oldRuns)],
      ['Old runs protected', String(data.protectedCount)],
      ['Workflows represented', String(data.workflowCount)],
      ['Rate-aware deletion budget', String(data.deletionBudget)],
      ['Selected this execution', String(data.selectedRuns)],
      ['Deleted', String(data.deleted)],
      ['Already gone', String(data.alreadyGone)],
      ['Failures', String(data.failures)],
      ['Eligible backlog after this execution', String(data.remaining)],
    ]);
  if (workflowRows.length > 0) {
    core.summary.addHeading('Deleted runs by workflow', 3).addTable([
      [{ data: 'Workflow', header: true }, { data: 'Deleted', header: true }],
      ...workflowRows,
    ]);
  }
  await core.summary.write();
}

async function run({ github, context, core, env = process.env }) {
  const retainDays = parseInteger(env.RETAIN_DAYS, 'RETAIN_DAYS', 30, 1, 3650);
  const keepMinimumRuns = parseInteger(env.KEEP_MINIMUM_RUNS, 'KEEP_MINIMUM_RUNS', 5, 0, 100);
  const requestedMaximum = parseInteger(env.MAX_DELETIONS, 'MAX_DELETIONS', 4000, 1, 4500);
  const parallelism = parseInteger(env.PARALLELISM, 'PARALLELISM', 4, 1, 12);
  const dryRun = parseBoolean(env.DRY_RUN, 'DRY_RUN');
  const cutoffSeconds = Math.floor((Date.now() - retainDays * 86400000) / 1000);
  const cutoffIso = isoFromSeconds(cutoffSeconds);
  const { owner, repo } = context.repo;

  core.info(`Retention=${retainDays}d cutoff=${cutoffIso} keep=${keepMinimumRuns} cap=${requestedMaximum} parallelism=${parallelism} dryRun=${dryRun}`);
  const oldRuns = await listOldCompletedRuns({ github, owner, repo, cutoffSeconds, core });
  const selection = selectDeletionCandidates(oldRuns, keepMinimumRuns);
  const rate = await github.rest.rateLimit.get();
  const deletionBudget = computeDeletionBudget(rate.data.resources.core.remaining, requestedMaximum, dryRun);
  const selected = selection.candidates.slice(0, deletionBudget);
  core.info(`Found ${oldRuns.length} old runs; ${selection.candidates.length} eligible; selecting ${selected.length}`);

  let result = { deleted: 0, alreadyGone: 0, failures: [], deletedByWorkflow: new Map() };
  if (dryRun) {
    selected.slice(0, 50).forEach((run) => core.info(`[dry run] ${run.id}\t${run.createdAt}\t${run.workflowName}`));
  } else if (selected.length > 0) {
    result = await deleteInParallel({ github, owner, repo, runs: selected, parallelism, core });
  }

  const remaining = Math.max(0, selection.candidates.length - result.deleted - result.alreadyGone);
  await summarize(core, {
    dryRun,
    retainDays,
    cutoffIso,
    oldRuns: oldRuns.length,
    protectedCount: selection.protectedCount,
    workflowCount: selection.workflowCount,
    deletionBudget,
    selectedRuns: selected.length,
    deleted: result.deleted,
    alreadyGone: result.alreadyGone,
    failures: result.failures.length,
    remaining,
    deletedByWorkflow: result.deletedByWorkflow,
  });
  core.setOutput('deleted', result.deleted);
  core.setOutput('remaining', remaining);
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
  pageRuns,
  run,
  selectDeletionCandidates,
};
