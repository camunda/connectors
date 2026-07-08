# Element Template Validator

Validates Camunda element templates (`connectors/**/element-templates/**/*.json`) against the
upstream JSON schema and a set of semantic rules.

## Build

```bash
mvn package -DskipTests -pl element-template-generator/validator -am
```

This produces an appassembler launcher at
`element-template-generator/validator/target/appassembler/bin/element-template-validator`.

## Run

```bash
# from the repo root, scans every element-templates/*.json under ./connectors
./element-template-generator/validator/target/appassembler/bin/element-template-validator

# scan a specific directory (overrides the default ./connectors)
./.../element-template-validator -d path/to/connectors
```

Exit code is `0` if no findings, `1` otherwise.

### Options

| Flag | Default | Description |
|------|---------|-------------|
| `-d`, `--directory` | `./connectors` | Root directory to scan for `element-templates/*.json` files. |
| `--schema-url` | pinned unpkg.com URL | HTTP/HTTPS URL of the JSON schema. Falls back to `CAMUNDA_TEMPLATE_SCHEMA_URL` env var, then the pinned default. Only `http`/`https` URLs are supported. |
| `--skip-directory` | `target, node_modules, .git, .idea, .m2` | Directory names pruned during traversal (by exact name, not path). Repeatable or comma-separated. When specified, replaces the default set entirely. |
| `--skip-connector` | `agentic-ai` | Connector directory names skipped entirely. Repeatable or comma-separated. When specified, replaces the default set entirely. |
| `--no-color` | off | Disable ANSI color in the output. Color is also auto-disabled when no console is attached (e.g. when output is piped or captured by CI). |

```bash
# override the schema URL
./.../element-template-validator --schema-url https://example.com/schema.json
# or via env var:
CAMUNDA_TEMPLATE_SCHEMA_URL=https://example.com/schema.json ./.../element-template-validator

# add a custom directory to skip (replaces the default set — include defaults if you still want them)
./.../element-template-validator --skip-directory target,node_modules,.git,.idea,.m2,my-dir

# skip an additional connector
./.../element-template-validator --skip-connector agentic-ai --skip-connector my-connector

# disable color (e.g. when piping output)
./.../element-template-validator --no-color | tee report.txt
```

The schema URL defaults to a **pinned** version
(`@camunda/zeebe-element-templates-json-schema@<version>`) so validator behavior is reproducible
across CI runs and local machines. Bump the constant in `SchemaRule.SCHEMA_VERSION` deliberately,
in lockstep with the connectors-team upgrade.

Files inside any `versioned/` directory are skipped by the single-file rules — they represent
frozen historical releases and are not modifiable retroactively. Multi-file rules still see them.

Connector directories listed in `--skip-connector` are skipped entirely. The default (`agentic-ai`)
covers templates generated/maintained outside the standard connector workflow that intentionally
diverge from the conventions enforced here.

## Rules

### Structural rules (always run; failures short-circuit semantic rules for that file)

| Rule id          | What it checks                                                                                                                                              |
|------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `json-parse`     | The file is syntactically valid JSON.                                                                                                                       |
| `duplicate-keys` | No object contains the same key twice. Mirrors the duplicate-key check the Camunda Web Modeler runs at editor save-time. Reports the offending line/column. |

### Single-file rules (run on every non-versioned template)

| Rule id                       | What it checks                                                                                                                                                                                                |
|-------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `schema`                      | Conformance to the upstream Camunda element-template JSON schema (fetched at startup).                                                                                                                        |
| `preset-target-exists`        | Each `presets` entry's key references an existing top-level property; if the property declares `choices`, the preset value must be one of them.                                                               |
| `condition-target-exists`     | Every `property` referenced from a `condition` (simple `{property, equals\|oneOf}` or compound `{allMatch: [...]}`) must exist in the template's top-level `properties[]`.                                    |
| `condition-value-in-choices`  | When a condition compares against a property that declares `choices`, the `equals` / `oneOf` value(s) must be one of the declared choices. Choice sets are unioned across duplicate ids (lenient by default). |
| `condition-property-order`    | Any property B with a `condition` (anywhere in its subtree, including nested `choices[*]` and `allMatch`) referencing property A must appear after A in `properties[]`. Forward-references and self-references are flagged. References to non-existent properties are skipped (handled by `condition-target-exists`). |
| `group-target-exists`         | Each property's `group` must reference a `groups[].id`.                                                                                                                                                       |
| `default-value-in-choices`    | For Dropdown properties with both a default `value` and a `choices` list, the default must be one of the choices.                                                                                             |
| `unique-group-id`             | No two `groups[]` entries may share an `id`.                                                                                                                                                                  |
| `unique-property-id`          | No two `properties[]` entries may share an `id` within the same template. Exception: duplicate-id properties whose visibility `condition`s are pairwise mutually exclusive (same gating `property`, disjoint `equals`/`oneOf` value sets) are tolerated — that's the established "switching" pattern (e.g. one Dropdown variant per parent operationGroup). Properties without a condition are always live, so any duplicate involving an unconditional property is still flagged. |
| `task-definition-binding-form`| The legacy binding type `zeebe:taskDefinition:type` is not allowed; use the canonical form `{ "type": "zeebe:taskDefinition", "property": "type" }`. The schema accepts both spellings, but generator output and all current templates use the canonical form. |
| `empty-group`                 | Every declared `groups[].id` must be referenced by at least one property.                                                                                                                                     |

### Operations-metadata rules

These rules enforce the `steps` / `presets` contract used by the Modeler search/discovery UI.
Connectors listed in `core/OperationMetadataIgnoreList` are exempt — other rules still run on those
connectors, only the ops-metadata rules below skip them. Remove an entry from the ignore list once
the connector's `@Searchable` annotations (or hand-authored JSON, for ET-only connectors) are in
place.

| Rule id                          | What it checks                                                                                                                                                                                                |
|----------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `steps-presets-present`          | Both `steps` and `presets` are present at the template root and are non-empty arrays.                                                                                                                         |
| `step-group-shape`               | Intermediate (group) step nodes have a non-empty `steps[]`; `description` is optional; **`keywords` is disallowed** (search aliases are leaf-only); no `presetId`.                                            |
| `step-leaf-shape`                | Leaf step nodes have a non-blank `presetId` and a non-empty `keywords` array; `description` is optional.                                                                                                      |
| `preset-id-unique`               | No two entries in `presets[]` share an `id`.                                                                                                                                                                  |
| `preset-id-resolves`             | Every leaf step's `presetId` resolves to some `presets[].id`.                                                                                                                                                 |
| `preset-target-exists`           | Each key inside `presets[].properties` references a template `properties[].id`; values match declared `choices` (union across duplicate-id declarations).                                                     |
| `preset-operation-group-consistency`    | Every property key referenced from `presets[].properties` is declared with `group == "operation"`. The `operation` group is the first entry in `groups[]`.                                                    |
| `preset-conditions-satisfied`    | For each preset, every pinned property's `condition` must hold under the preset's own assignment. Catches mutually-exclusive value pairings (e.g. setting `operationGroup=A` alongside a property gated on `operationGroup=B`). |
| `preset-coverage`                | The set of `presets[]` and the set of leaf `steps[]` each enumerate **exactly** the reachable leaves of the discriminator tree (counts add across conditional branches, not multiply). Missing leaves, orphans, and duplicates are errors. |

### Multi-file rules (run once over the full set, including `versioned/`)

| Rule id                          | What it checks                                                                                                                                                                                |
|----------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `hybrid-parity`                  | For every `hybrid/<base>-hybrid.json`, the non-hybrid sibling must exist and declare the same `properties[].id` and `groups[].id`. Known-intentional differences (`taskDefinitionType`, `connectorType` on the hybrid side; `deduplication*`, `consumeUnmatchedEvents`, `messageTtl` on the non-hybrid side) are allowlisted. Additionally, `steps` and `presets` must be deep-equal between hybrid and non-hybrid (skipped for connectors on the ops-metadata ignore list). |
| `versioned-template-consistency` | For each `versioned/<base>-<N>.json`, the file's `version` field must equal `N`. Files whose filename suffix starts with `0` (e.g. `-0`, `-01`, `-02`) are pre-versioning snapshots — a missing `version` field is tolerated, but a present one must still match. |
| `element-template-version-consistency` | When a template declares a property bound to the `elementTemplateVersion` task header (`binding.key == "elementTemplateVersion"`, `binding.type == "zeebe:taskHeader"`), that property's `value` must equal the template's top-level `version` (as a string). Only fires when the bound property is present; runs on `versioned/` snapshots too, where the value must match the snapshot's own `version`. |
| `current-version-bump`           | For each non-versioned template, the `version` field must be exactly one higher than the highest-versioned snapshot under the same `element-templates/` subtree that shares the same `id`. If no snapshot shares the id (e.g. an intentional `.vN` → `.v(N+1)` rename starts a new lineage), the rule is silent. |
| `unique-id-version`              | No two templates (current or versioned) may share both the same `id` and the same `version`. The Modeler dedupes by id+version on import, so duplicates collide silently. Catches snapshots that weren't promoted to a new version and content changes that forgot to bump the version. |

The schema rule runs first as a gate. If it produces findings for a file, semantic
rules are skipped for that file to avoid noisy cascades.

## Adding a rule

1. Implement `io.camunda.connector.validator.core.Rule` (single-file) or
   `io.camunda.connector.validator.core.MultiFileRule` (cross-file).
2. Add it to the corresponding list in `ValidatorCommand`.
3. Write a fixture-driven unit test in `src/test/java/.../rule/`.
