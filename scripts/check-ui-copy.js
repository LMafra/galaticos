#!/usr/bin/env node
/**
 * UX-PLAN-21: guard banned UI patterns in ClojureScript sources.
 */
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..', 'src-cljs', 'galaticos', 'components');
const ALLOW_CONFIRM = new Set([]);

const rules = [
  {
    name: 'no-js-confirm',
    pattern: /js\/confirm|window\.confirm/g,
    message: 'Use delete-undo toast instead of blocking confirm (UX-PLAN-03)',
    filter: (file) => !ALLOW_CONFIRM.has(path.basename(file)),
  },
];

function walk(dir, files = []) {
  for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, ent.name);
    if (ent.isDirectory()) walk(p, files);
    else if (ent.name.endsWith('.cljs')) files.push(p);
  }
  return files;
}

let failed = false;
for (const file of walk(ROOT)) {
  const content = fs.readFileSync(file, 'utf8');
  const rel = path.relative(path.join(__dirname, '..'), file);
  for (const rule of rules) {
    if (rule.filter && !rule.filter(file)) continue;
    const matches = content.match(rule.pattern);
    if (matches && matches.length > 0) {
      console.error(`${rel}: ${rule.message} (${matches.length} hit(s))`);
      failed = true;
    }
  }
}

if (failed) {
  process.exit(1);
}
console.log('check-ui-copy: OK');
