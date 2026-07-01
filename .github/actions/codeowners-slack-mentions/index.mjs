// Resolve the CODEOWNERS owners of a set of repo paths and map them to Slack
// mentions. Pure derivation, no GitHub API or Slack calls. Env inputs:
//   CO_PATHS             newline/space-separated repo-relative paths
//   CO_CODEOWNERS_FILE   path to CODEOWNERS (default: CODEOWNERS)
//   CO_TEAM_SLACK_MAP    inline JSON or a .json path: "@org/team" -> mention
//   CO_ALWAYS_INCLUDE    space-separated mention(s) always included
// CLI: `node index.mjs --path <p>` prints the owners of <p>.

import {readFileSync, appendFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';

const SEP = '/';

const quoteMeta = (char) => char.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

// GitHub CODEOWNERS (gitignore-style) matching, compiled to a regexp.

// Anchoring rules: leading slash → root; single segment → any depth; trailing
// slash → directory.
function normalizePatternSegments(pattern) {
  let segments = pattern.split(SEP);

  if (segments[0] === '') {
    segments = segments.slice(1);
  } else if (segments.length === 1 || (segments.length === 2 && segments[1] === '')) {
    if (segments[0] !== '**') segments = ['**', ...segments];
  }

  if (segments.length > 1 && segments[segments.length - 1] === '') {
    segments[segments.length - 1] = '**';
  }

  return segments;
}

// `needSlash` = whether the next concrete segment must emit its own separator.
function doubleStarToRegExp(isFirst, isLast) {
  if (isFirst && isLast) return {fragment: '.+', needSlash: false};
  if (isFirst) return {fragment: `(?:.+${SEP})?`, needSlash: false};
  if (isLast) return {fragment: `${SEP}.*`, needSlash: false};
  return {fragment: `(?:${SEP}.+)?`, needSlash: true};
}

function segmentToRegExp(segment) {
  let fragment = '';
  let escaped = false;
  for (const char of segment) {
    if (escaped) {
      escaped = false;
      fragment += quoteMeta(char);
    } else if (char === '\\') {
      escaped = true;
    } else if (char === '*') {
      fragment += `[^${SEP}]*`;
    } else if (char === '?') {
      fragment += `[^${SEP}]`;
    } else {
      fragment += quoteMeta(char);
    }
  }
  return fragment;
}

export function patternToRegExp(pattern) {
  if (pattern.includes('***'))
    throw new Error('pattern cannot contain three consecutive asterisks');
  if (pattern === '') throw new Error('empty pattern');
  if (pattern === '/') return /^$/;

  const segments = normalizePatternSegments(pattern);
  const lastIndex = segments.length - 1;
  let source = '^';
  let needSlash = false;

  segments.forEach((segment, i) => {
    const isLast = i === lastIndex;

    if (segment === '**') {
      const {fragment, needSlash: next} = doubleStarToRegExp(i === 0, isLast);
      source += fragment;
      needSlash = next;
      return;
    }

    if (needSlash) source += SEP;
    if (segment === '*') {
      source += `[^${SEP}]+`; // whole-segment "*" requires at least one char
    } else {
      source += segmentToRegExp(segment);
      if (isLast) source += `(?:${SEP}.*)?`; // final segment also owns its descendants
    }
    needSlash = true;
  });

  return new RegExp(`${source}$`);
}

export function parseCodeowners(text) {
  const rules = [];
  for (const rawLine of text.split('\n')) {
    const line = rawLine.split('#')[0].trim(); // strip full-line and inline comments
    if (!line || line.startsWith('[')) continue;

    const [pattern, ...owners] = line.split(/\s+/);
    try {
      rules.push({pattern, owners, regex: patternToRegExp(pattern)});
    } catch {
      // skip malformed pattern
    }
  }
  return rules;
}

const stripLeadingSlashes = (path) => path.replace(/^\/+/, '');

// Last matching rule wins; a rule with no owners removes ownership.
export function ownersFor(path, rules) {
  const subject = stripLeadingSlashes(path);
  let owners = [];
  for (const rule of rules) if (rule.regex.test(subject)) owners = rule.owners;
  return owners;
}

function pushUnique(list, value) {
  if (value && !list.includes(value)) list.push(value);
  return list;
}

// Map values and always-include are already-formatted mentions, used verbatim.
// A missing/empty value adds nothing, so unmapped owners fall back to always-include.
export function deriveMentions({paths, rules, teamMap, alwaysInclude}) {
  const owners = [];
  for (const path of paths)
    for (const owner of ownersFor(path, rules)) pushUnique(owners, owner);

  const mentions = [];
  for (const mention of alwaysInclude) pushUnique(mentions, mention);
  for (const owner of owners) pushUnique(mentions, teamMap[owner]);

  return {owners, mentions};
}

const splitWs = (value) =>
  (value ?? '')
    .split(/\s+/)
    .map((token) => token.trim())
    .filter(Boolean);

// Inline JSON, or a path to a .json file.
export function parseTeamMap(input) {
  const value = (input || '').trim();
  if (!value) return {};
  if (value.startsWith('{')) return JSON.parse(value);
  return JSON.parse(readFileSync(value, 'utf8'));
}

function setOutput(name, value) {
  const outputFile = process.env.GITHUB_OUTPUT;
  if (!outputFile) {
    console.log(`${name}=${value}`);
    return;
  }
  appendFileSync(outputFile, `${name}=${value}\n`);
}

function printOwnersForPath(codeownersFile, path) {
  const rules = parseCodeowners(readFileSync(codeownersFile, 'utf8'));
  console.log(ownersFor(path, rules).join(' '));
}

function emitMentionsFromEnv(codeownersFile) {
  const paths = splitWs(process.env.CO_PATHS);
  const alwaysInclude = splitWs(process.env.CO_ALWAYS_INCLUDE);

  const rules = parseCodeowners(readFileSync(codeownersFile, 'utf8'));
  const teamMap = parseTeamMap(process.env.CO_TEAM_SLACK_MAP);
  const {owners, mentions} = deriveMentions({paths, rules, teamMap, alwaysInclude});

  console.log(
    `[codeowners-slack-mentions] paths=${paths.length} ` +
      `owners=${owners.join(',') || '(none)'} mentions=${mentions.join(' ') || '(none)'}`,
  );
  setOutput('owners', owners.join(' '));
  setOutput('mentions', mentions.join(' '));
}

function main() {
  const argv = process.argv.slice(2);
  const codeownersFile = (process.env.CO_CODEOWNERS_FILE || 'CODEOWNERS').trim();

  const pathFlagIndex = argv.indexOf('--path');
  if (pathFlagIndex !== -1) {
    printOwnersForPath(codeownersFile, argv[pathFlagIndex + 1] ?? '');
    return;
  }

  emitMentionsFromEnv(codeownersFile);
}

const isMain = process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1];
if (isMain) main();
