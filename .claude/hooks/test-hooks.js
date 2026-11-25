// Offline unit tests for the three hook scripts. Run: node .claude/hooks/test-hooks.js
const { spawnSync } = require('child_process');
const fs = require('fs');
const path = require('path');
const root = path.resolve(__dirname, '..', '..');
const env = Object.assign({}, process.env, { CLAUDE_PROJECT_DIR: root });
const G = '.claude/hooks/pre-tool-guard.sh';
const P = '.claude/hooks/post-edit-check.sh';
const S = '.claude/hooks/subagent-stop-audit.sh';
let fail = 0;

function t(name, want, payload, script = G, raw) {
  const r = spawnSync('bash', [script], { input: raw !== undefined ? raw : JSON.stringify(payload), env, cwd: root, encoding: 'utf8' });
  const ok = r.status === want;
  if (!ok) fail++;
  console.log((ok ? 'PASS' : 'FAIL') + ` [${r.status} want ${want}] ${name}` + (r.stderr ? ' -- ' + r.stderr.trim().replace(/\n/g, ' ').slice(0, 110) : ''));
}
const abs = f => path.join(root, f);
const w = (agent, f, tool = 'Write', extra = {}) => ({ tool_name: tool, agent_type: agent, tool_input: Object.assign({ file_path: abs(f) }, extra) });
const b = (c, agent) => ({ tool_name: 'Bash', agent_type: agent, tool_input: { command: c } });
const m = (tool, agent, input = {}) => ({ tool_name: tool, agent_type: agent, tool_input: input });

// --- pre-tool-guard: shell
t('gh pr merge blocked', 2, b('gh pr merge 5'));
t('force push blocked', 2, b('git push --force origin main'));
t('force push -f blocked', 2, b('git push -f'));
t('normal push ok', 0, b('git push origin feature'));
t('pr create unauthorized blocked', 2, b('gh pr create --title x', 'pr'));
t('rm -rf / blocked', 2, b('rm -rf /'));
t('rm -rf ./docs blocked', 2, b('rm -rf ./docs'));
t('rm -rf node_modules ok', 0, b('rm -rf node_modules'));
t('rm single file ok', 0, b('rm foo.txt'));
t('sub bash state redirect blocked', 2, b('echo {} > workflow/workflow-state.json', 'planning'));
t('sub bash state read ok', 0, b('cat workflow/workflow-state.json', 'planning'));
// --- pre-tool-guard: writes
t('state write by sub blocked', 2, w('planning', 'workflow/workflow-state.json'));
t('state write by orchestrator ok', 0, w(undefined, 'workflow/workflow-state.json'));
t('requirements own doc ok', 0, w('requirements', '.claude/docs/requirements.md'));
t('requirements writes arch blocked', 2, w('requirements', '.claude/docs/architecture.md'));
t('design-review edits arch ok', 0, w('design-review', '.claude/docs/architecture.md', 'Edit'));
t('orchestrator writes doc blocked', 2, w(undefined, '.claude/docs/requirements.md'));
t('orchestrator status-line edit ok', 0, w(undefined, '.claude/docs/requirements.md', 'Edit', { old_string: '**Status:** draft', new_string: '**Status:** approved' }));
t('orchestrator multi-line edit blocked', 2, w(undefined, '.claude/docs/requirements.md', 'Edit', { old_string: '**Status:** a\nx', new_string: '**Status:** b' }));
t('orchestrator writes src blocked', 2, w(undefined, 'src/app.js'));
t('impl src before stage 4 blocked', 2, w('implementation', 'src/app.js'));
t('impl README before stage 4 blocked', 2, w('implementation', 'README.md'));
t('impl docs/usage.md ok', 0, w('implementation', 'docs/usage.md'));
t('impl writes owned doc blocked', 2, w('implementation', '.claude/docs/impl-plan.md'));
t('impl writes workflow blocked', 2, w('implementation', 'workflow/x.json'));
t('code-review src blocked', 2, w('code-review', 'src/app.js'));
t('code-review own doc ok', 0, w('code-review', '.claude/docs/code-review.md'));
t('pr writes changelog ok', 0, w('pr', 'CHANGELOG.md'));
t('planning writes changelog blocked', 2, w('planning', 'CHANGELOG.md'));
t('.codemie blocked', 2, w('implementation', '.codemie/x'));
t('impl writes .claude/agents blocked', 2, w('implementation', '.claude/agents/x.md'));
t('impl writes .claude/hooks blocked', 2, w('implementation', '.claude/hooks/pre-tool-guard.sh', 'Edit'));
t('impl writes .claude/docs/notes ok', 0, w('implementation', '.claude/docs/notes.md'));
t('requirements writes own .claude/docs ok', 0, w('requirements', '.claude/docs/requirements.md'));
t('outside project ok', 0, { tool_name: 'Write', agent_type: 'verification', tool_input: { file_path: path.join(require('os').tmpdir(), 'x.txt') } });
t('unix-style path resolved + blocked', 2, { tool_name: 'Write', agent_type: 'requirements', tool_input: { file_path: root.replace(/\\/g, '/').replace(/^([A-Za-z]):/, (m, d) => '/' + d.toLowerCase()) + '/docs/architecture.md' } });
t('malformed input fails open', 0, null, G, 'not json');
t('ns: plugin-namespaced agent name normalized', 2, w('agentic-sdlc:planning', 'workflow/workflow-state.json'));

// --- MCP (no authorization in state)
t('mcp github merge blocked', 2, m('mcp__github__merge_pull_request', 'pr'));
t('mcp github delete_repository blocked', 2, m('mcp__github__delete_repository'));
t('mcp github create_pull_request unauthorized blocked', 2, m('mcp__github__create_pull_request', 'pr'));
t('mcp github issue_write blocked', 2, m('mcp__github__issue_write', 'pr'));
t('mcp github read ok', 0, m('mcp__github__get_file_contents', 'pr'));
t('mcp atlassian getJiraIssue ok', 0, m('mcp__atlassian__getJiraIssue'));
t('mcp atlassian JQL ok', 0, m('mcp__atlassian__searchJiraIssuesUsingJql'));
t('mcp atlassian executeRead ok', 0, m('mcp__atlassian__executeRead'));
t('mcp atlassian createJiraIssue without authorization blocked', 2, m('mcp__atlassian__createJiraIssue', undefined, { projectKey: 'CC' }));
t('mcp atlassian comment blocked', 2, m('mcp__atlassian__addOrEditJiraIssueComment'));
t('mcp atlassian editJiraIssue blocked', 2, m('mcp__atlassian__editJiraIssue'));
t('mcp atlassian transition blocked', 2, m('mcp__atlassian__transitionJiraIssue'));
t('mcp atlassian executeWrite blocked', 2, m('mcp__atlassian__executeWrite'));
t('mcp atlassian executeDestructive blocked', 2, m('mcp__atlassian__executeDestructive'));
t('mcp atlassian createConfluenceContent blocked', 2, m('mcp__atlassian__createConfluenceContent'));
t('mcp other server ignored', 0, m('mcp__other__merge_pull_request'));

// --- state-dependent (state restored afterwards)
const sp = abs('workflow/workflow-state.json');
const orig = fs.readFileSync(sp, 'utf8');
try {
  const s = JSON.parse(orig); s.stages['4'].approved = true; s.pr_authorized = true; s.jira_writeback_authorized = true; s.jira = { project_key: 'CC' };
  fs.writeFileSync(sp, JSON.stringify(s));
  t('impl src after stage 4 ok', 0, w('implementation', 'src/app.js'));
  t('pr create authorized by pr ok', 0, b('gh pr create --title x', 'pr'));
  t('pr create authorized by orchestrator blocked', 2, b('gh pr create --title x'));
  t('mcp github create_pull_request authorized by pr ok', 0, m('mcp__github__create_pull_request', 'pr'));
  t('mcp github create_pull_request by orchestrator blocked', 2, m('mcp__github__create_pull_request'));
  t('mcp github merge still blocked when authorized', 2, m('mcp__github__merge_pull_request', 'pr'));
  t('mcp createJiraIssue CC authorized ok', 0, m('mcp__atlassian__createJiraIssue', undefined, { projectKey: 'CC', cloudId: 'x', issueType: 'Epic', summary: 's' }));
  t('mcp createJiraIssue lowercase key ok', 0, m('mcp__atlassian__createJiraIssue', undefined, { projectKey: 'cc' }));
  t('mcp createJiraIssue other project blocked', 2, m('mcp__atlassian__createJiraIssue', undefined, { projectKey: 'AG' }));
  t('mcp createJiraIssue without projectKey blocked', 2, m('mcp__atlassian__createJiraIssue', undefined, { summary: 's' }));
  t('mcp createJiraIssue by subagent blocked', 2, m('mcp__atlassian__createJiraIssue', 'requirements', { projectKey: 'CC' }));
  t('mcp comment blocked even when authorized', 2, m('mcp__atlassian__addOrEditJiraIssueComment', undefined, { projectKey: 'CC' }));
  t('mcp executeDestructive blocked even when authorized', 2, m('mcp__atlassian__executeDestructive'));
} finally { fs.writeFileSync(sp, orig); }

// --- post-edit-check
t('post: secret flagged', 2, { tool_name: 'Write', tool_input: { file_path: abs('src/c.js'), content: 'const k = "AKIAABCDEFGHIJKLMNOP";' } }, P);
t('post: clean write ok', 0, { tool_name: 'Write', tool_input: { file_path: abs('src/c.js'), content: 'const a = 1;' } }, P);
t('post: valid state ok', 0, { tool_name: 'Write', tool_input: { file_path: sp } }, P);
t('post: ignores Read', 0, { tool_name: 'Read', tool_input: { file_path: sp } }, P);
t('post: malformed fails open', 0, null, P, '{{');

// --- subagent-stop-audit
const auditLog = abs('workflow/audit.log');
const hadLog = fs.existsSync(auditLog); const logBefore = hadLog ? fs.readFileSync(auditLog, 'utf8') : null;
const ho = o => '```handoff\n' + JSON.stringify(o) + '\n```';
try {
  t('audit: non-sdlc agent ignored', 0, { agent_type: 'Explore', last_assistant_message: 'x' }, S);
  t('audit: missing handoff rejected', 2, { agent_type: 'requirements', last_assistant_message: 'done' }, S);
  t('audit: stop_hook_active allowed', 0, { agent_type: 'requirements', stop_hook_active: true, last_assistant_message: 'done' }, S);
  t('audit: bad json rejected', 2, { agent_type: 'requirements', last_assistant_message: '```handoff\n{bad\n```' }, S);
  t('audit: needs_input ok without artifact', 0, { agent_type: 'requirements', last_assistant_message: ho({ status: 'needs_input' }) }, S);
  t('audit: complete w/ template artifact rejected', 2, { agent_type: 'requirements', last_assistant_message: ho({ status: 'complete' }) }, S);
  t('audit: implementation needs evidence', 2, { agent_type: 'implementation', last_assistant_message: ho({ status: 'complete' }) }, S);
  t('audit: implementation with evidence ok', 0, { agent_type: 'implementation', last_assistant_message: ho({ status: 'complete', evidence: ['npm test -> exit 0'] }) }, S);
  t('audit: no message fails open', 0, { agent_type: 'requirements' }, S);
} finally {
  if (hadLog) fs.writeFileSync(auditLog, logBefore); else if (fs.existsSync(auditLog)) fs.unlinkSync(auditLog);
}

console.log(fail ? fail + ' FAILED' : 'ALL OK');
process.exit(fail ? 1 : 0);
