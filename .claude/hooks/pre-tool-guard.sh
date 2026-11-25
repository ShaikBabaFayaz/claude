#!/usr/bin/env bash
# PreToolUse guard for the SDLC framework. Exit 2 blocks the tool call; any parse problem fails open (exit 0).
command -v node >/dev/null 2>&1 || exit 0

JS=$(cat <<'NODE_EOF'
const fs = require('fs'), path = require('path');
let raw = '';
process.stdin.on('data', d => raw += d).on('end', () => {
  let input;
  try { input = JSON.parse(raw); } catch (e) { process.exit(0); }
  try { main(input); } catch (e) { process.exit(0); }
  process.exit(0);
});

function block(msg) { process.stderr.write('SDLC guard blocked this action: ' + msg + '\n'); process.exit(2); }
function winPath(p) { return p.replace(/^\/([a-zA-Z])\//, (m, d) => d.toUpperCase() + ':\\'); }

const OWNERS = {
  '.claude/docs/requirements.md': ['requirements'],
  '.claude/docs/architecture.md': ['architecture', 'design-review'],
  '.claude/docs/design-review.md': ['design-review'],
  '.claude/docs/impl-plan.md': ['planning'],
  '.claude/docs/code-review.md': ['code-review'],
  '.claude/docs/verification-report.md': ['verification'],
  '.claude/docs/pr-description.md': ['pr'],
  'CHANGELOG.md': ['pr']
};
const SUBAGENTS = ['requirements', 'architecture', 'design-review', 'planning', 'implementation', 'code-review', 'verification', 'pr'];
const FRAMEWORK_PREFIXES = ['docs/', 'workflow/', '.claude/'];
const FRAMEWORK_FILES = ['CLAUDE.md', 'CHANGELOG.md'];
const DANGEROUS_TARGETS = ['/', '~', '.', '..', '*', './', './*', '.git', '.claude', 'docs', 'workflow', '.codemie', '$HOME', '${HOME}'];

function main(i) {
  const root = winPath(process.env.CLAUDE_PROJECT_DIR || i.cwd || process.cwd());
  const agent = String(i.agent_type || 'orchestrator').replace(/^.*:/, ''); // strip plugin namespace
  const isSub = SUBAGENTS.includes(agent);
  const tool = i.tool_name || '';
  const inp = i.tool_input || {};

  let state = null;
  try { state = JSON.parse(fs.readFileSync(path.join(root, 'workflow', 'workflow-state.json'), 'utf8')); } catch (e) {}

  if (tool.startsWith('mcp__')) return checkMcp(tool, inp, agent, state);
  if (tool === 'Bash' || tool === 'PowerShell') return checkShell(inp.command || '', agent, state);
  if (['Write', 'Edit', 'MultiEdit', 'NotebookEdit'].includes(tool)) return checkWrite(inp, root, agent, isSub, state);
}

const GITHUB_ALWAYS_BLOCKED = /^(merge_pull_request|delete_repository|delete_file|create_repository|fork_repository)$/;
const GITHUB_PR_STAGE_WRITES = /^(create_pull_request|create_branch|push_files|create_or_update_file|update_pull_request|update_pull_request_branch)$/;
const GITHUB_WRITE = /^(add_|create_|update_|delete_|merge_|push_|fork_|request_|issue_write$|pull_request_review_write$|sub_issue_write$)/;
const ATLASSIAN_WRITE = /^(create|edit|addOrEdit|transition|update|executeWrite$|executeDestructive$|addGraphContext$)/;

function checkMcp(tool, inp, agent, state) {
  const parts = tool.split('__');
  const name = parts[parts.length - 1];
  const server = parts.slice(1, -1).join('__');
  if (/github/.test(server)) {
    if (GITHUB_ALWAYS_BLOCKED.test(name)) block('GitHub "' + name + '" is never allowed in the SDLC workflow (merging a PR is a separate user decision).');
    if (GITHUB_PR_STAGE_WRITES.test(name)) {
      if (!state || state.pr_authorized !== true) block('GitHub "' + name + '" requires explicit user authorization (workflow-state.json pr_authorized must be true).');
      if (agent !== 'pr') block('only the pr agent may use GitHub "' + name + '".');
      return;
    }
    if (GITHUB_WRITE.test(name)) block('GitHub write tool "' + name + '" is outside the SDLC workflow.');
    return;
  }
  if (/atlassian/.test(server) && ATLASSIAN_WRITE.test(name)) {
    if (name !== 'createJiraIssue') block('Atlassian "' + name + '" is not allowed; the framework only creates Jira issues (createJiraIssue), after user authorization.');
    if (agent !== 'orchestrator') block('only the orchestrator may create Jira issues.');
    if (!state || state.jira_writeback_authorized !== true) block('creating Jira issues needs explicit user authorization (workflow-state.json jira_writeback_authorized must be true).');
    const allowedKey = state.jira && state.jira.project_key;
    if (!allowedKey) block('no Jira project key is recorded in workflow-state.json (jira.project_key).');
    const given = inp.projectKey || inp.project_key || (inp.fields && inp.fields.project && inp.fields.project.key);
    if (!given) block('createJiraIssue must pass projectKey.');
    if (String(given).toUpperCase() !== String(allowedKey).toUpperCase()) block('createJiraIssue may only target Jira project ' + allowedKey + ', not ' + given + '.');
  }
}

function checkShell(cmd, agent, state) {
  if (/\bgh\s+pr\s+merge\b/i.test(cmd)) block('merging a PR is a separate user decision and is never automatic.');
  if (/\bgit\s+push\b[^;&|\n]*(\s--force\b|\s-f\b|--force-with-lease|\s\+\S)/.test(cmd)) block('force-push is not allowed.');
  if (/\bgh\s+pr\s+create\b/i.test(cmd)) {
    if (!state || state.pr_authorized !== true) block('gh pr create requires explicit user authorization (workflow-state.json pr_authorized must be true).');
    if (agent !== 'pr') block('only the pr agent may create the Pull Request.');
  }
  const recursiveRm = /\brm\b[^;&|\n]*\s(-[a-zA-Z]*[rR][a-zA-Z]*|--recursive)\b/.test(cmd);
  const recursivePs = /\bRemove-Item\b[^;&|\n]*-Recurse/i.test(cmd);
  if (recursiveRm || recursivePs) {
    const tokens = cmd.split(/[\s;&|]+/).map(t => t.replace(/^['"]|['"]$/g, '').replace(/\\/g, '/').replace(/\/+$/, (m, o, s) => s.length > 1 ? '' : m));
    for (const t of tokens) {
      if (!t || t.startsWith('-')) continue;
      const bare = t.replace(/^\.\//, '');
      if (DANGEROUS_TARGETS.includes(t) || DANGEROUS_TARGETS.includes(bare)) block('recursive delete of "' + t + '" is not allowed.');
      if (/^(\/|[A-Za-z]:\/?)[^/]*$/.test(t) && t !== 'rm') block('recursive delete of top-level path "' + t + '" is not allowed.');
      if (/(^|\/)\.codemie(\/|$)/.test(t)) block('.codemie must not be modified.');
    }
  }
  if (agent !== 'orchestrator' && /workflow-state\.json/.test(cmd) && /(>|>>|\btee\b|\bsed\s+-i|\bmv\b|\bcp\b|\brm\b|Set-Content|Out-File|Add-Content)/.test(cmd)) {
    block('only the orchestrator may modify workflow/workflow-state.json.');
  }
}

function checkWrite(inp, root, agent, isSub, state) {
  const target = inp.file_path || inp.notebook_path || '';
  if (!target) return;
  const rel = path.relative(root, path.resolve(winPath(target))).replace(/\\/g, '/');
  if (rel.startsWith('..') || path.isAbsolute(rel)) return; // outside project (e.g. temp dir): not governed here

  if (rel === '.codemie' || rel.startsWith('.codemie/')) block('.codemie must not be modified.');

  if (rel === 'workflow/workflow-state.json' && agent !== 'orchestrator') block('only the orchestrator may modify workflow/workflow-state.json.');
  if (rel === 'workflow/audit.log' && agent !== 'orchestrator') block('audit.log is written by hooks only.');

  const owners = OWNERS[rel];
  if (owners) {
    if (isSub && !owners.includes(agent)) block(agent + ' may not write ' + rel + ' (owned by: ' + owners.join(', ') + ').');
    if (!isSub) { // orchestrator: only one-line status/approval header edits
      const o = inp.old_string, n = inp.new_string;
      const headerOnly = typeof o === 'string' && typeof n === 'string' && !/\n/.test(o) && !/\n/.test(n) && /Status|Approved by user/.test(o) && /Status|Approved by user/.test(n);
      if (!headerOnly) block('the orchestrator delegates artifact content to the ' + owners[0] + ' agent; it may only edit the Status/Approved header lines.');
    }
    return;
  }

  const inFramework = FRAMEWORK_FILES.includes(rel) || FRAMEWORK_PREFIXES.some(p => rel.startsWith(p));

  if (agent === 'orchestrator' && !rel.startsWith('workflow/')) block('the orchestrator may only write workflow/ files; delegate everything else to a subagent.');

  if (isSub && agent !== 'implementation' && !rel.startsWith('.claude/docs/')) {
    block(agent + ' may only write its own artifact; ' + rel + ' is outside its remit.');
  }
  if (agent === 'implementation' && rel.startsWith('workflow/')) block('implementation may not touch workflow/.');
  if (isSub && rel.startsWith('.claude/') && !rel.startsWith('.claude/docs/')) block(agent + ' may not modify framework files under .claude/ (only .claude/docs/).');

  if (!inFramework && state && state.stages && state.stages['4'] && state.stages['4'].approved !== true) {
    block('source/project files cannot be written until Stage 4 (implementation plan) is approved. Target: ' + rel);
  }
}
NODE_EOF
)
exec node -e "$JS"
