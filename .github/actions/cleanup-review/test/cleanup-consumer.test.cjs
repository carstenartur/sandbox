// Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync, spawnSync } = require('node:child_process');
const { test } = require('node:test');
const action = path.resolve(__dirname, '..');
const before = 'package example;\nimport java.nio.charset.Charset;\nclass Example {\n'
  + '  Charset first() { return Charset.forName("UTF-8"); }\n'
  + '  // Unchanged context\n'.repeat(12)
  + '  Charset last() { return Charset.forName("UTF-8"); }\n}\n';

// The runner and Git are real. Only Docker is replaced at the process boundary;
// transformation accuracy is covered separately by the opt-in container test.
function consumer(t, projects = [''], extra = {}) {
  const temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'sandbox-consumer-'));
  t.after(() => fs.rmSync(temporary, { recursive: true, force: true }));
  const cwd = path.join(temporary, 'external repository'), output = path.join(temporary, 'evidence');
  fs.mkdirSync(cwd);
  const put = (name, content) => { const file = path.join(cwd, name); fs.mkdirSync(path.dirname(file), { recursive: true }); fs.writeFileSync(file, content); };
  const git = (...args) => execFileSync('git', args, { cwd, stdio: ['pipe', 'pipe', 'pipe'] }).toString().trim();
  git('init', '-q'); git('config', 'user.name', 'Consumer Test'); git('config', 'user.email', 'test@example.invalid');
  git('config', 'core.autocrlf', 'false');
  for (const [name, content] of Object.entries(extra)) put(name, content);
  for (const project of projects) {
    const prefix = project ? project + '/' : '';
    put(prefix + '.project', '<projectDescription><name>' + (project || 'consumer') + '</name><natures><nature>org.eclipse.jdt.core.javanature</nature></natures></projectDescription>\n');
    put(prefix + '.classpath', '<classpath><classpathentry kind="src" path="src"/><classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER"/><classpathentry kind="output" path="bin"/></classpath>\n');
    put(prefix + '.settings/org.eclipse.jdt.core.prefs', 'eclipse.preferences.version=1\norg.eclipse.jdt.core.compiler.compliance=11\n');
    put(prefix + 'src/example/Example.java', before);
  }
  put('README.md', 'Independent consumer; no Sandbox source tree.\n');
  git('add', '.'); git('commit', '-qm', 'base');
  const base = git('rev-parse', 'HEAD');
  for (const project of projects) put((project ? project + '/' : '') + 'src/example/Example.java', before + '// PR edit\n');
  if (!projects.length) put('src/example/Example.java', before);
  git('add', '.'); git('commit', '-qm', 'PR head');
  const head = git('rev-parse', 'HEAD');
  const mock = path.join(temporary, 'docker'), log = path.join(temporary, 'docker.jsonl');
  fs.writeFileSync(mock, '#!' + process.execPath + '\n' + `
const fs = require('node:fs');
const args = process.argv.slice(2);
fs.appendFileSync(process.env.DOCKER_LOG, JSON.stringify(args) + '\\n');
if (args[0] === 'image') { console.log('example.invalid/cleanup@sha256:fixture'); process.exit(0); }
if (args[0] !== 'run') process.exit(0);
const volumes = args.filter((_, i) => args[i-1] === '--volume');
const root = volumes.find(v => v.endsWith(':/workspace')).slice(0, -11);
const config = args[args.indexOf('--config')+1];
const mapping = volumes.find(v => v.includes(':' + config + ':ro'));
const hostConfig = mapping ? mapping.slice(0, mapping.indexOf(':' + config)) : root + config.slice('/workspace'.length);
const profile = fs.readFileSync(hostConfig, 'utf8');
if (!profile.includes('cleanup.explicit_encoding_keep_behavior=true')) throw new Error('Wrong default profile');
if (process.env.MOCK_NO_CHANGE) process.exit(0);
for (let i = 0; i < args.length; i++) {
 if (args[i] !== '--source') continue;
 const file = root + args[++i].slice('/workspace'.length);
 if (!file.endsWith('.java')) continue;
 let text = fs.readFileSync(file, 'utf8');
 text = text.replace('import java.nio.charset.Charset;', 'import java.nio.charset.Charset;\\nimport java.nio.charset.StandardCharsets;')
   .replaceAll('Charset.forName("UTF-8")', 'StandardCharsets.UTF_8');
 fs.writeFileSync(file, text);
}
`);
  fs.chmodSync(mock, 0o755);
  const run = (options = [], moreEnv = {}) => {
    const outputs = path.join(temporary, 'outputs'); fs.writeFileSync(outputs, '');
    const result = spawnSync('bash', [path.join(action, 'run-cleanup-review.sh'), '--base-sha', base, '--head-sha', head,
      '--image', 'fixture', '--output-dir', output, ...options], { cwd, encoding: 'utf8',
      env: { ...process.env, GITHUB_OUTPUT: outputs, DOCKER_BIN: mock, DOCKER_LOG: log, ...moreEnv } });
    return { ...result, outputs: fs.readFileSync(outputs, 'utf8'), summary: fs.existsSync(path.join(output, 'summary.md'))
      ? fs.readFileSync(path.join(output, 'summary.md'), 'utf8') : '',
      calls: fs.existsSync(log) ? fs.readFileSync(log, 'utf8').trim().split('\n').map(JSON.parse) : [] };
  };
  return { cwd, put, git, base, head, run, output, temporary };
}
function succeeds(result) { assert.equal(result.status, 0, result.stdout + result.stderr); }
const custom = { 'profiles/my profile.properties': 'cleanup.explicit_encoding_keep_behavior=true\n' };

test('an external Eclipse project uses the shipped default without copying Sandbox files or changing metadata', (t) => {
  const fixture = consumer(t, [''], { 'pom.xml': '<project/>\n' });
  const metadata = ['.project', '.classpath', '.settings/org.eclipse.jdt.core.prefs', 'pom.xml'];
  const original = metadata.map(name => fs.readFileSync(path.join(fixture.cwd, name)));
  const result = fixture.run(); succeeds(result);
  assert.match(result.outputs, /analysis_status=changes-proposed/);
  assert.match(result.outputs, /project_count=1/);
  assert.equal(fixture.git('diff', '--name-only'), 'src/example/Example.java');
  assert.deepEqual(metadata.map(name => fs.readFileSync(path.join(fixture.cwd, name))), original);
  assert.equal(fixture.git('diff', '--numstat'), '3\t2\tsrc/example/Example.java');
  assert.ok(!fs.existsSync(path.join(fixture.cwd, '.github')));
  const config = result.calls.find(args => args[0] === 'run');
  assert.ok(config.some(arg => arg.endsWith(':/review-config/cleanup.properties:ro')));
});

test('nested Eclipse projects below a non-Java aggregator are both imported without conversion', (t) => {
  const fixture = consumer(t, ['plug in', 'tests'], { '.project': '<projectDescription><natures/></projectDescription>\n' });
  const result = fixture.run(); succeeds(result);
  assert.match(result.outputs, /project_count=2/);
  assert.deepEqual(result.calls.filter(args => args[0] === 'run').map(args => args[args.indexOf('--import-project')+1]),
    ['/workspace/plug in', '/workspace/tests']);
  assert.equal(fixture.git('diff', '--name-only', '--', '*.project', '*.classpath', '*.prefs'), '');
});

test('an explicit custom profile with spaces works and a missing custom profile is not silently replaced', (t) => {
  succeeds(consumer(t, [''], custom).run(['--config-file', 'profiles/my profile.properties']));
  const missing = consumer(t, ['']).run(['--config-file', 'missing.properties']);
  assert.notEqual(missing.status, 0); assert.match(missing.stderr, /configuration does not exist/i);
  assert.equal(missing.calls.length, 0);
});

for (const buildFile of ['pom.xml', 'build.gradle', 'build.gradle.kts']) {
  test(buildFile + ' alone cannot report a successful no-change Eclipse analysis', (t) => {
    const result = consumer(t, [], { [buildFile]: '// build metadata\n', ...custom }).run(['--config-file', 'profiles/my profile.properties']);
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /not analysed|not analyzed/i);
    assert.match(result.outputs, /analysis_status=no-supported-projects/);
    assert.match(result.summary, /Eclipse|Maven|Gradle/);
    assert.equal(result.calls.length, 0);
  });
}

test('partial project coverage fails before Docker by default and can be explicitly downgraded to a warning', (t) => {
  const extra = { '.project': '<projectDescription><natures/></projectDescription>\n', 'Other.java': 'class Other{}\n', ...custom };
  for (const policy of ['error', 'warn']) {
    const fixture = consumer(t, ['module'], extra);
    fixture.put('Other.java', 'class Other{ int changed; }\n'); fixture.git('add', '.'); fixture.git('commit', '--amend', '--no-edit', '-q');
    const head = fixture.git('rev-parse', 'HEAD');
    const result = fixture.run(['--head-sha', head, '--config-file', 'profiles/my profile.properties', '--unmatched-files', policy]);
    assert.match(result.outputs, /analysis_status=partial-analysis/);
    if (policy === 'error') { assert.notEqual(result.status, 0); assert.equal(result.calls.length, 0); }
    else { succeeds(result); assert.match(result.summary, /not analysed|not analyzed/i); }
  }
});

test('no Java input and a completed run without modifications are different outcomes', (t) => {
  const empty = consumer(t);
  succeeds(empty.run(['--base-sha', empty.head]));
  const noInput = empty.run(['--base-sha', empty.head]);
  assert.match(noInput.outputs, /analysis_status=no-java-changes/);
  assert.equal(noInput.calls.length, 0);
  const checked = consumer(t).run([], { MOCK_NO_CHANGE: 'true' }); succeeds(checked);
  assert.match(checked.outputs, /analysis_status=no-cleanup-changes/);
  assert.ok(checked.calls.some(args => args[0] === 'run'));
});

test('unrelated Git histories fail instead of being classified as no Java input', (t) => {
  const fixture = consumer(t, [''], custom);
  const unrelated = fixture.git('commit-tree', fixture.git('rev-parse', 'HEAD^{tree}'), '-m', 'unrelated root');
  const result = fixture.run(['--base-sha', unrelated, '--config-file', 'profiles/my profile.properties']);
  assert.notEqual(result.status, 0);
  assert.equal(result.calls.length, 0);
});

test('the consumer workflow and guide agree and explicitly exclude unprivileged fork and Dependabot PRs', () => {
  const root = path.resolve(action, '../../..');
  const workflow = fs.readFileSync(path.join(root, 'examples/cleanup-review/eclipse/.github/workflows/cleanup.yml'), 'utf8');
  const guide = fs.readFileSync(path.join(root, 'GITHUB_ACTIONS.md'), 'utf8');
  const shown = guide.split('<!-- consumer-workflow:start -->\n```yaml\n')[1]?.split('```\n<!-- consumer-workflow:end -->')[0];
  assert.equal(shown, workflow);
  assert.ok(workflow.includes('head.repo.full_name == github.repository'));
  assert.ok(workflow.includes("user.login != 'dependabot[bot]'"));
  assert.ok(!workflow.includes('pull_request_target'));
  assert.ok(workflow.includes('uses: carstenartur/sandbox/.github/actions/cleanup-review@'));
  assert.ok(!workflow.includes('config-file:'));
  assert.ok(!workflow.includes('sandbox_common_test'));
});

test('explicit warning mode retains a no-supported-projects result instead of a clean-code message', (t) => {
  const result = consumer(t, [], { 'pom.xml': '<project/>\n' }).run(['--unmatched-files', 'warn']);
  succeeds(result);
  assert.match(result.outputs, /analysis_status=no-supported-projects/);
  assert.match(result.stdout, /did not analyse any project/);
  assert.equal(result.calls.length, 0);
});

test('all entry pages describe the current review action rather than automatic PR-branch writes', () => {
  const root = path.resolve(action, '../../..');
  for (const name of ['README.md', 'GITHUB_ACTIONS.md', '.github/workflows/README.md']) {
    const document = fs.readFileSync(path.join(root, name), 'utf8');
    assert.ok(!document.includes('not active for automatic PR cleanup'), name);
    assert.ok(!document.includes('Commit changes back to the PR'), name);
    assert.ok(!document.includes('Not directly - this action is specific'), name);
    assert.ok(document.includes('cleanup-review'), name);
  }
});
