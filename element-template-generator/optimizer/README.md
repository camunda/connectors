# Element Template Optimizer

Compacts Camunda element templates by collapsing conditional properties into smaller, semantically-equivalent forms.

The optimizer runs three passes in sequence:

1. **merge-by-identity** — merges properties that differ only in their condition value
2. **totalize** — drops conditions that cover every discriminator value (current implementation considers only `Dropdown` properties as discriminators; a hidden property with a constant value would also be a tautological discriminator but is not yet recognised)
3. **strength-reduce** — rewrites singleton `oneOf` as `equals`

## Why use it?

REST-based element templates frequently contain dozens of conditional properties that are byte-identical except for the operation they're gated on. A 5-operation API often emits 30+ such properties; many can collapse into one. A realistic prototype went from **42 → 23 properties (−45%)**, verified semantically equivalent.

### Example

Before:

```json
{
  "properties": [
    {
      "id": "search_query_locale",
      "value": "en-US",
      "binding": {"type": "zeebe:input", "name": "locale"},
      "condition": {"property": "operationId", "equals": "search"}
    },
    {
      "id": "autocomplete_query_locale",
      "value": "en-US",
      "binding": {"type": "zeebe:input", "name": "locale"},
      "condition": {"property": "operationId", "equals": "autocomplete"}
    },
    {
      "id": "feed_query_locale",
      "value": "en-US",
      "binding": {"type": "zeebe:input", "name": "locale"},
      "condition": {"property": "operationId", "equals": "feed"}
    }
  ]
}
```

After:

```json
{
  "properties": [
    {
      "id": "query_locale",
      "value": "en-US",
      "binding": {"type": "zeebe:input", "name": "locale"},
      "condition": {
        "property": "operationId",
        "oneOf": ["search", "autocomplete", "feed"]
      }
    }
  ]
}
```

If the merged `oneOf` covers every value of its discriminator, `totalize` drops the condition entirely.

## Integration into the generation pipeline

The optimizer is wired into `congen-cli`'s `generate` subcommand. Every template emitted by the generator is run through `Optimizer.defaultPipeline()` before being serialized to disk, so generated artifacts only ever ship in the compacted form. There is no flag — compaction is the default.

```bash
# Templates produced here are already optimized.
congen generate openapi path/to/spec.yaml > template.json
```

To consume the optimizer as a library:

```java
import io.camunda.connector.optimizer.core.Optimizer;

ElementTemplate compacted = Optimizer.defaultPipeline().optimize(template);
```

## Standalone CLI

For one-shot cleanup of hand-maintained templates that bypass the generator:

```bash
# Rewrite a single template file in place
element-template-optimizer template.json

# Write the result to a separate file
element-template-optimizer template.json -o template.optimized.json

# Preview without writing
element-template-optimizer --dry-run template.json

# Run a subset of passes
element-template-optimizer --skip-passes=strength-reduce template.json

# List available passes
element-template-optimizer list-passes
```

Build the standalone binary with `mvn -pl optimizer package`; the executable lands in `target/appassembler/bin/element-template-optimizer`.

## Testing

Unit tests cover every pass with focused fixtures. The behavioural-equivalence suite (`OptimizerPropertyTest`) generates multi-operation templates parameterically and asserts that — for every discriminator value — applying the optimized template via `element-templates-cli` produces a BPMN diagram identical (modulo whitespace and attribute order) to applying the original.

```bash
mvn -pl optimizer test
```

The equivalence suite requires the npm-published `element-templates-cli` binary on `$PATH`. If it's missing, the class is reported as **skipped** locally and as a **hard failure** in CI (when `CI=true` or `OPTIMIZER_REQUIRE_CLI=true`), so the gate cannot vanish silently behind a green build.

Pin the version to match what CI uses (see `.github/workflows/package.json`):

```bash
npm i -g element-templates-cli@0.5.0
```

The CLI API differs between major versions — running against an unpinned version may produce passes that don't actually verify equivalence.

## Architecture

Each pass implements:

```java
public interface Pass {
  String id();
  ElementTemplate apply(ElementTemplate template);
  String description();
}
```

Passes are pure functions over the typed `ElementTemplate` IR from `element-template-generator-core` — no raw JSON. Composition lives in `Optimizer`:

```java
Optimizer.defaultPipeline().optimize(template);
Optimizer.defaultPipelineExcept(List.of("strength-reduce")).optimize(template);
```

## License

Apache 2.0
