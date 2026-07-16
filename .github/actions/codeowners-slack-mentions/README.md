# codeowners-slack-mentions

Resolve the **CODEOWNERS owners** of a set of repo paths and map them to **Slack
mentions**, with optional always-include mentions, deduped.

Pure derivation — it makes **no GitHub API calls** and **does not post to Slack**.
You give it paths + a team→mention map; it gives you back the mentions to ping.
Its only dependency is [`minimatch`](https://www.npmjs.com/package/minimatch)
(see "Why minimatch" below); the action installs it via `npm ci` before running.

## Inputs

| Input | Required | Default | Description |
|---|---|---|---|
| `paths` | no | `""` | Newline- or space-separated repo-relative paths to resolve owners for. Empty → only `always-include` is returned. |
| `codeowners-file` | no | `CODEOWNERS` | Path to the CODEOWNERS file. |
| `team-slack-map` | yes | — | JSON mapping `"@org/team"` → Slack mention (`<!subteam^…>` or `<@…>`), inserted verbatim. Either **inline JSON** or a **path** to a `.json` file — inline lets a caller assemble it from secrets without writing them to disk. |
| `always-include` | no | `""` | Already-formatted mention(s) always included regardless of owners (e.g. a medic group `<!subteam^…>`). Space-separated, passed through verbatim. |

## Outputs

| Output | Description |
|---|---|
| `mentions` | Space-joined, deduped Slack mentions — `always-include` first, then mapped owners. |
| `owners` | Space-joined, deduped CODEOWNERS owner handles resolved from the paths (useful for logging). |

## Behaviour

- Matching follows GitHub's CODEOWNERS (gitignore-style) semantics: full glob
  support (`*`, `**`, `?`), anchoring, directory matching, **last match wins**.
- `team-slack-map` values are used verbatim, so they must be fully-formatted mentions
  (`<!subteam^…>` or `<@…>`), the same form as `always-include`.
- An owner that isn't present in `team-slack-map`, or whose value is empty, contributes
  no mention (never a broken reference) — use `always-include` (e.g. a medic group)
  as the catch-all so nothing is silent.

## Why minimatch

Pattern matching used to be a hand-rolled glob-to-regex compiler. It's now
delegated to [`minimatch`](https://www.npmjs.com/package/minimatch) (npm's own
glob matcher; one transitive dependency, `brace-expansion`, itself dependency-free)
instead of a generic gitignore library, because plain gitignore engines (e.g.
`ignore`) recurse into matched directories even for bare wildcard patterns like
`docs/*` — GitHub's CODEOWNERS docs explicitly say that pattern must *not* match
nested files. Anchoring and "does this pattern also own its descendants" stay as
our own small CODEOWNERS-specific rules; `[...]` ranges and leading `!` are
escaped before matching, since CODEOWNERS treats both literally, unlike
`.gitignore`/minimatch defaults.

## Example

```yaml
- uses: ./.github/actions/codeowners-slack-mentions
  id: mentions
  with:
    paths: |
      connectors-e2e-test/connectors-e2e-test-agentic-ai
      connectors-e2e-test/connectors-e2e-test-soap
    # Inline JSON assembled from secrets (values are full mentions):
    team-slack-map: |
      {
        "@camunda/connectors-agentic-ai": "${{ secrets.AGENTIC_ORCHESTRATION_MEDIC_SLACK_GROUP_ID }}"
      }
    always-include: ${{ secrets.CONNECTORS_MEDIC_SLACK_GROUP_ID }}

- run: echo "Ping ${{ steps.mentions.outputs.mentions }}"
```

`team-slack-map` also accepts a path to a committed `.json` file if you prefer to keep the
mapping in the repo instead of secrets.

## Tests

```bash
node --test .github/actions/codeowners-slack-mentions/index.test.mjs
```
