// Resolve the CODEOWNERS owners of a set of repo paths and map them to Slack
// mentions. Pure derivation, no GitHub API or Slack calls. Env inputs:
//   CO_PATHS             newline/space-separated repo-relative paths
//   CO_CODEOWNERS_FILE   path to CODEOWNERS (default: CODEOWNERS)
//   CO_TEAM_SLACK_MAP    inline JSON or a .json path: "@org/team" -> mention
//   CO_ALWAYS_INCLUDE    space-separated mention(s) always included
// CLI: `node index.mjs --path <p>` prints the owners of <p>.

import {readFileSync, appendFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {minimatch} from 'minimatch';

const SEP = '/';

// GitHub CODEOWNERS (gitignore-style) matching. The glob mechanics (`*`,
// `**`, `?`, escaping) are delegated to minimatch; CODEOWNERS-specific
// anchoring/ownership rules stay here. minimatch extensions CODEOWNERS
// doesn't define (brace expansion, extglob) are disabled.
const MINIMATCH_OPTS = {dot: true, nobrace: true, noext: true};

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

// Unlike plain gitignore/minimatch, CODEOWNERS doesn't support "[...]"
// character ranges (matched literally) or leading "!" negation.
const escapeGlobChars = (segment) => segment.replace(/[[\]]/g, '\\$&');

function toGlobPattern(segments) {
  const glob = segments.map((s) => (s === '**' ? s : escapeGlobChars(s))).join(SEP);
  return glob.startsWith('!') ? `\\${glob}` : glob;
}

export function patternToMatcher(pattern) {
  if (pattern.includes('***'))
    throw new Error('pattern cannot contain three consecutive asterisks');
  if (pattern === '') throw new Error('empty pattern');
  if (pattern === '/') return () => false;

  const segments = normalizePatternSegments(pattern);
  const glob = toGlobPattern(segments);
  const lastSegment = segments[segments.length - 1];

  // A final segment with no wildcard could be a directory, so it also owns
  // everything beneath it; a final "*"/"**" already means "everything here".
  const alsoOwnsDescendants = lastSegment !== '*' && lastSegment !== '**';

  const baseRe = minimatch.makeRe(glob, MINIMATCH_OPTS);
  if (!baseRe) throw new Error(`invalid pattern: ${pattern}`);
  const descendantRe = alsoOwnsDescendants
    ? minimatch.makeRe(`${glob}/**`, MINIMATCH_OPTS)
    : null;

  return (subject) => baseRe.test(subject) || Boolean(descendantRe?.test(subject));
}

export function parseCodeowners(text) {
  const rules = [];
  for (const rawLine of text.split('\n')) {
    const line = rawLine.split('#')[0].trim(); // strip full-line and inline comments
    if (!line || line.startsWith('[')) continue;

    const [pattern, ...owners] = line.split(/\s+/);
    try {
      rules.push({pattern, owners, matches: patternToMatcher(pattern)});
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
  for (const rule of rules) if (rule.matches(subject)) owners = rule.owners;
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
