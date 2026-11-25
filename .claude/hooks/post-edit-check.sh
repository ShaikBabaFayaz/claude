#!/usr/bin/env bash
# PostToolUse sanity checks after Write/Edit. Exit 2 only feeds stderr back to Claude (the edit already happened). Fails open.
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
function warn(msgs) { process.stderr.write('SDLC post-edit check:\n- ' + msgs.join('\n- ') + '\n'); process.exit(2); }

const ARTIFACTS = ['.claude/docs/requirements.md', '.claude/docs/architecture.md', '.claude/docs/design-review.md', '.claude/docs/impl-plan.md', '.claude/docs/code-review.md', '.claude/docs/verification-report.md', '.claude/docs/pr-description.md'];
const SECRET_PATTERNS = [
  [/AKIA[0-9A-Z]{16}/, 'AWS access key id'],
  [/-----BEGIN (RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----/, 'private key'],
  [/ghp_[A-Za-z0-9]{36}/, 'GitHub token'],
  [/\bsk-[A-Za-z0-9_-]{32,}/, 'API secret key'],
  [/(password|passwd|secret|api[_-]?key|token)\s*[:=]\s*['"][^'"\s]{8,}['"]/i, 'hard-coded credential']
];

function main(i) {
  if (!['Write', 'Edit', 'MultiEdit'].includes(i.tool_name)) return;
  const inp = i.tool_input || {};
  const target = inp.file_path || '';
  if (!target) return;
  const root = winPath(process.env.CLAUDE_PROJECT_DIR || i.cwd || process.cwd());
  const abs = path.resolve(winPath(target));
  const rel = path.relative(root, abs).replace(/\\/g, '/');
  if (rel.startsWith('..')) return;
  const problems = [];

  if (rel === 'workflow/workflow-state.json') {
    try {
      const s = JSON.parse(fs.readFileSync(abs, 'utf8'));
      if (!s.stages || Object.keys(s.stages).length !== 8) problems.push('workflow-state.json must keep all 8 stages.');
    } catch (e) { problems.push('workflow-state.json is no longer valid JSON: ' + e.message); }
  }

  if (ARTIFACTS.includes(rel)) {
    try {
      const head = fs.readFileSync(abs, 'utf8').split('\n').slice(0, 12).join('\n');
      if (!/\*\*Status:\*\*/.test(head)) problems.push(rel + ' must keep its "**Status:**" header line near the top.');
    } catch (e) {}
  }

  if (!rel.startsWith('.claude/')) {
    const text = [inp.content, inp.new_string].filter(x => typeof x === 'string').join('\n');
    for (const [re, label] of SECRET_PATTERNS) {
      if (re.test(text)) problems.push('possible ' + label + ' written to ' + rel + '. If real, remove it and use environment/config instead.');
    }
  }
  if (problems.length) warn(problems);
}
NODE_EOF
)
exec node -e "$JS"
