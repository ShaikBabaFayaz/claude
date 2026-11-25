#!/usr/bin/env bash
# SubagentStop audit: require a valid handoff block, and a real artifact when status is "complete". Appends to workflow/audit.log. Fails open.
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

function winPath(p) { return p.replace(/^\/([a-zA-Z])\//, (m, d) => d.toUpperCase() + ':\\'); }

const EXPECTED = {
  'requirements': ['.claude/docs/requirements.md'],
  'architecture': ['.claude/docs/architecture.md'],
  'design-review': ['.claude/docs/design-review.md'],
  'planning': ['.claude/docs/impl-plan.md'],
  'code-review': ['.claude/docs/code-review.md'],
  'verification': ['.claude/docs/verification-report.md'],
  'pr': ['.claude/docs/pr-description.md', 'CHANGELOG.md']
};
const STATUSES = ['complete', 'needs_input', 'blocked', 'failed'];

function log(root, entry) {
  try {
    fs.mkdirSync(path.join(root, 'workflow'), { recursive: true });
    fs.appendFileSync(path.join(root, 'workflow', 'audit.log'), JSON.stringify(Object.assign({ ts: new Date().toISOString() }, entry)) + '\n');
  } catch (e) {}
}
function reject(root, agent, reason) {
  log(root, { agent, event: 'rejected', reason });
  process.stderr.write('SDLC audit: cannot finish yet - ' + reason + '\n');
  process.exit(2);
}

function main(i) {
  const agent = i.agent_type || '';
  if (!(agent in EXPECTED) && agent !== 'implementation') return;
  const root = winPath(process.env.CLAUDE_PROJECT_DIR || i.cwd || process.cwd());
  const msg = typeof i.last_assistant_message === 'string' ? i.last_assistant_message : null;

  if (msg === null) { log(root, { agent, event: 'stop', note: 'no last_assistant_message in hook input; not audited' }); return; }
  if (i.stop_hook_active === true) { log(root, { agent, event: 'stop', note: 'stop_hook_active; allowed' }); return; }

  const m = msg.match(/```handoff\s*([\s\S]*?)```/);
  if (!m) reject(root, agent, 'final message must end with a ```handoff fenced JSON block (see CLAUDE.md).');
  let h;
  try { h = JSON.parse(m[1]); } catch (e) { reject(root, agent, 'the handoff block is not valid JSON: ' + e.message); }
  if (!STATUSES.includes(h.status)) reject(root, agent, 'handoff.status must be one of ' + STATUSES.join(', ') + '.');

  if (h.status === 'complete') {
    for (const rel of (EXPECTED[agent] || [])) {
      let txt = '';
      try { txt = fs.readFileSync(path.join(root, rel), 'utf8'); } catch (e) {}
      if (!txt.trim()) reject(root, agent, rel + ' is missing or empty but status is complete.');
      if (/\*\*Status:\*\*\s*not started/i.test(txt)) reject(root, agent, rel + ' is still the untouched template.');
    }
    if (agent === 'implementation' && !(Array.isArray(h.evidence) && h.evidence.length)) {
      reject(root, agent, 'implementation must list the commands run (with exit codes) in handoff.evidence.');
    }
    if (agent === 'verification' && !(Array.isArray(h.evidence) && h.evidence.length)) {
      reject(root, agent, 'verification must list every command run (with exit codes) in handoff.evidence.');
    }
  }
  log(root, { agent, event: 'stop', status: h.status, artifacts: h.artifacts || [] });
}
NODE_EOF
)
exec node -e "$JS"
