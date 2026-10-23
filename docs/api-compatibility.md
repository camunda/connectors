# API compatibility check

Modules that third parties compile against are gated by a [revapi](https://revapi.org/) check. The
build fails on a binary or source incompatible change to such a module, compared with a released
baseline version, unless the change is explicitly accepted.

## Gated modules

| Module           | API                                       |
|------------------|-------------------------------------------|
| `connector-sdk/core` | All public types in `io.camunda.connector.api` |

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
to the released version: on the release branch for every release, and on `main` for the first
release of a minor. Merge it like the other release follow-up pull requests. When backporting this
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

The baseline bump clears all accepted breaks, because they are part of the new baseline.
