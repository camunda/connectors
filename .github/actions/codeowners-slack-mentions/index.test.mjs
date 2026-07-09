// Run: node --test .github/actions/codeowners-slack-mentions/index.test.mjs
// Matching cases follow GitHub's documented CODEOWNERS behaviour; the final
// suite checks this repo's real CODEOWNERS.

import {describe, it} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {
  parseCodeowners,
  patternToMatcher,
  parseTeamMap,
  ownersFor,
  deriveMentions,
} from './index.mjs';

const GLOBAL = '*       @global-owner1 @global-owner2';
const owners = (codeowners, path) => ownersFor(path, parseCodeowners(codeowners));

describe('CODEOWNERS reference examples', () => {
  it('"*" is the default owner for everything', () => {
    assert.deepEqual(owners(GLOBAL, 'anything/here.py'), [
      '@global-owner1',
      '@global-owner2',
    ]);
  });

  it('"*.js @js-owner" wins over the global owner (last match takes precedence)', () => {
    // trailing inline comment must be stripped, not parsed as owners
    const f = `${GLOBAL}\n*.js    @js-owner #This is an inline comment.`;
    assert.deepEqual(owners(f, 'app.js'), ['@js-owner']);
    assert.deepEqual(owners(f, 'app.py'), ['@global-owner1', '@global-owner2']);
  });

  it('"*.go docs@example.com" supports email owners', () => {
    assert.deepEqual(owners(`${GLOBAL}\n*.go docs@example.com`, 'main.go'), [
      'docs@example.com',
    ]);
  });

  it('"*.txt @octo-org/octocats" supports team owners', () => {
    assert.deepEqual(owners(`${GLOBAL}\n*.txt @octo-org/octocats`, 'notes.txt'), [
      '@octo-org/octocats',
    ]);
  });

  it('"/build/logs/ @doctocat" owns that root dir and its subdirectories only', () => {
    const f = `${GLOBAL}\n/build/logs/ @doctocat`;
    assert.deepEqual(owners(f, 'build/logs/error.log'), ['@doctocat']);
    assert.deepEqual(owners(f, 'build/logs/deep/trace.log'), ['@doctocat']);
    assert.deepEqual(owners(f, 'src/build/logs/error.log'), [
      '@global-owner1',
      '@global-owner2',
    ]);
  });

  it('"docs/* docs@example.com" matches one level deep but NOT nested files', () => {
    const f = `${GLOBAL}\ndocs/* docs@example.com`;
    assert.deepEqual(owners(f, 'docs/getting-started.md'), ['docs@example.com']);
    assert.deepEqual(owners(f, 'docs/build-app/troubleshooting.md'), [
      '@global-owner1',
      '@global-owner2',
    ]);
  });

  it('"apps/ @octocat" owns an apps directory anywhere in the repo', () => {
    const f = `${GLOBAL}\napps/ @octocat`;
    assert.deepEqual(owners(f, 'apps/index.js'), ['@octocat']);
    assert.deepEqual(owners(f, 'src/apps/index.js'), ['@octocat']);
  });

  it('"/docs/ @doctocat" owns the root docs dir and its subdirectories', () => {
    const f = `${GLOBAL}\n/docs/ @doctocat`;
    assert.deepEqual(owners(f, 'docs/readme.md'), ['@doctocat']);
    assert.deepEqual(owners(f, 'docs/api/spec.md'), ['@doctocat']);
  });

  it('"/scripts/ @doctocat @octocat" supports multiple owners', () => {
    assert.deepEqual(owners(`${GLOBAL}\n/scripts/ @doctocat @octocat`, 'scripts/deploy.sh'), [
      '@doctocat',
      '@octocat',
    ]);
  });

  it('"**/logs @octocat" owns a /logs directory at any nesting level', () => {
    const f = `${GLOBAL}\n**/logs @octocat`;
    assert.deepEqual(owners(f, 'logs/app.log'), ['@octocat']);
    assert.deepEqual(owners(f, 'build/logs/app.log'), ['@octocat']);
    assert.deepEqual(owners(f, 'deeply/nested/logs/app.log'), ['@octocat']);
  });

  it('an owner-less pattern removes ownership (e.g. /apps/github)', () => {
    const f = `${GLOBAL}\n/apps/ @octocat\n/apps/github`;
    assert.deepEqual(owners(f, 'apps/server.js'), ['@octocat']);
    assert.deepEqual(owners(f, 'apps/github/server.js'), []);
  });

  it('a later, more specific pattern overrides an earlier one (/apps/github @doctocat)', () => {
    const f = `${GLOBAL}\n/apps/ @octocat\n/apps/github @doctocat`;
    assert.deepEqual(owners(f, 'apps/github/server.js'), ['@doctocat']);
    assert.deepEqual(owners(f, 'apps/other/server.js'), ['@octocat']);
  });

  it('character ranges are unsupported and treated literally', () => {
    const f = `${GLOBAL}\n/file[1].txt @ranges`;
    assert.deepEqual(owners(f, 'file[1].txt'), ['@ranges']);
    assert.deepEqual(owners(f, 'file1.txt'), ['@global-owner1', '@global-owner2']);
  });
});

describe('patternToMatcher', () => {
  const matches = (pattern, path) => patternToMatcher(pattern)(path);

  it('a whole-segment "*" requires at least one char; embedded "*" allows zero', () => {
    assert.ok(matches('docs/*', 'docs/x'));
    assert.ok(!matches('docs/*', 'docs/'));
    assert.ok(matches('*.md', 'README.md'));
    assert.ok(matches('*.md', '.md'));
  });

  it('"**" matches across segments at every position', () => {
    assert.ok(matches('**', 'a/b/c'));
    assert.ok(matches('**/x', 'a/b/x'));
    assert.ok(matches('x/**', 'x/a/b'));
    assert.ok(matches('a/**/b', 'a/b'));
    assert.ok(matches('a/**/b', 'a/x/y/b'));
  });

  it('throws on three consecutive asterisks', () => {
    assert.throws(() => patternToRegExp('a/***/b'));
  });
});

describe('parseTeamMap', () => {
  it('parses inline JSON (starts with "{")', () => {
    assert.deepEqual(parseTeamMap('{"@org/a": "<!subteam^S1>"}'), {'@org/a': '<!subteam^S1>'});
    assert.deepEqual(parseTeamMap('  \n {"@org/a":"x"} '), {'@org/a': 'x'});
  });

  it('returns an empty map for empty/absent input', () => {
    assert.deepEqual(parseTeamMap(''), {});
    assert.deepEqual(parseTeamMap(undefined), {});
  });
});

describe('deriveMentions', () => {
  const FIXTURE = `
${GLOBAL}
*.js @js-owner
/scripts/ @doctocat @octocat
/apps/ @octocat
/apps/github
`;
  const rules = parseCodeowners(FIXTURE);
  const teamMap = {
    '@js-owner': '<!subteam^S_JS>',
    '@doctocat': '<!subteam^S_DOC>',
    '@octocat': '<!subteam^S_OCT>',
  };

  it('inserts map mentions verbatim, leads with always-include, dedupes, preserves order', () => {
    const {mentions} = deriveMentions({
      paths: ['app.js', 'scripts/deploy.sh', 'README'],
      rules,
      teamMap,
      alwaysInclude: ['<!subteam^MEDIC>'],
    });
    assert.deepEqual(mentions, [
      '<!subteam^MEDIC>',
      '<!subteam^S_JS>',
      '<!subteam^S_DOC>',
      '<!subteam^S_OCT>',
    ]);
  });

  it('empty paths yields just always-include', () => {
    const {mentions} = deriveMentions({paths: [], rules, teamMap, alwaysInclude: ['<!subteam^MEDIC>']});
    assert.deepEqual(mentions, ['<!subteam^MEDIC>']);
  });

  it('a no-owner path contributes nothing beyond always-include', () => {
    const {owners: o, mentions} = deriveMentions({
      paths: ['apps/github/server.js'],
      rules,
      teamMap,
      alwaysInclude: ['<!subteam^MEDIC>'],
    });
    assert.deepEqual(o, []);
    assert.deepEqual(mentions, ['<!subteam^MEDIC>']);
  });

  it('empty map value is skipped (no broken mention)', () => {
    const {mentions} = deriveMentions({
      paths: ['app.js'],
      rules,
      teamMap: {'@js-owner': ''},
      alwaysInclude: ['<!subteam^MEDIC>'],
    });
    assert.deepEqual(mentions, ['<!subteam^MEDIC>']);
  });

  it('always-include is deduped against itself', () => {
    const {mentions} = deriveMentions({
      paths: [],
      rules,
      teamMap: {},
      alwaysInclude: ['<!subteam^MEDIC>', '<!subteam^MEDIC>'],
    });
    assert.deepEqual(mentions, ['<!subteam^MEDIC>']);
  });
});

describe('this repo CODEOWNERS', () => {
  const rules = parseCodeowners(
    readFileSync(new URL('../../../CODEOWNERS', import.meta.url), 'utf8'),
  );

  it('agentic-ai e2e module routes to the agentic-ai team', () => {
    assert.ok(
      ownersFor('connectors-e2e-test/connectors-e2e-test-agentic-ai', rules).includes(
        '@camunda/connectors-agentic-ai',
      ),
    );
  });

  it('an unlisted e2e module falls through to the default owner', () => {
    assert.deepEqual(ownersFor('connectors-e2e-test/connectors-e2e-test-soap', rules), [
      '@camunda/connectors',
    ]);
  });

  it('a pom.xml is excluded from ownership', () => {
    assert.deepEqual(ownersFor('connectors/email/pom.xml', rules), []);
  });
});
