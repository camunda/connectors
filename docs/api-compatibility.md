# API compatibility check

Modules that third parties compile against are gated by a [revapi](https://revapi.org/) check. The
build fails on a binary or source incompatible change to such a module, compared with a released
baseline version, unless the change is explicitly accepted.

## Gated modules

| Module                                  | API                                                                 |
|-----------------------------------------|---------------------------------------------------------------------|
| `connector-sdk/core`                    | All public types in `io.camunda.connector.api`                      |
| `connectors/agentic-ai/aiagent-api`     | All public types, the AI Agent extension points                     |
| `connectors/agentic-ai/aiagent-model`   | All public types, the agent model used by the extension points      |

The AI Agent modules are skipped until their first release (8.11.0) is the baseline, see
`revapi.skip` in `connectors/agentic-ai/pom.xml`. Their element templates, request models and
persisted data are not covered by this check.

Only the module's own classes count as API. Types of its dependencies that appear in its signatures
are not checked.

To gate a module, declare the plugin in its `pom.xml` (the configuration is managed in
`parent/pom.xml`), add an `api-changes.json` next to it (copy `connector-sdk/core/api-changes.json`),
and add the module to the `Check API compatibility` step in `.github/workflows/TEST_FEATURE_BRANCH.yml`.

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.revapi</groupId>
      <artifactId>revapi-maven-plugin</artifactId>
    </plugin>
  </plugins>
</build>
```

## Running the check

The check is bound to `verify`:

```bash
./mvnw -pl connector-sdk/core -am verify -DskipTests
```

Skip it locally with `-Drevapi.skip`.

## Baseline

The baseline is the `api.baseline.version` property in `parent/pom.xml`:

- `main` compares against the first release of the last released minor (for example `8.10.0`).
- A stable branch compares against its last released patch.

The `bump-api-baseline` job of the release workflow opens a pull request that moves the baseline
to the released version: on `stable/x.y` for every `x.y.z` release, and on `main` for the first
release of a minor. The release branch itself (`release-x.y.z`) is not targeted, because it is merged
back into `stable/x.y` independently. Merge it like the other release follow-up pull requests. When backporting this
setup to a stable branch, set the baseline to that branch's last released patch.

## Accepting a break

If a break is intended, record it in the module's `api-changes.json`. On a failure, the plugin
output prints the entry to copy. Every entry needs a `justification` that says why the break is
acceptable and where it is documented for users:

```json
[
  {
    "extension": "revapi.differences",
    "configuration": {
      "ignore": true,
      "differences": [
        {
          "code": "java.method.removed",
          "old": "method void io.camunda.connector.api.Example::removed()",
          "justification": "Replaced by Example::replacement(), see <link to the release notes or migration guide>"
        }
      ]
    }
  }
]
```

The baseline bump removes the accepted breaks that the released version contains, i.e. those in the
`api-changes.json` of its tag. Breaks accepted after the release, for example on `main` after the
release branch was cut, are kept.

`.github/workflows/scripts/check_api_changes.py` checks in CI that every accepted break has a
justification. For the AI Agent modules, the justification must also link an existing anchor of
[`connectors/agentic-ai/docs/breaking-changes.md`](../connectors/agentic-ai/docs/breaking-changes.md),
for example `see docs/breaking-changes.md#module-split`.
