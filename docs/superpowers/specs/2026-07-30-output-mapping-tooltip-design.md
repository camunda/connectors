# Help tooltip for connector response/output mapping (issue #154)

## Problem

The `Result expression` field (FEEL "output mapping") in the properties panel gives
users no in-app hint about which variables are available (`response.status`,
`response.headers`, `response.body`, …) or how to write the destructuring FEEL
expression. Users have to leave the modeler and read docs to figure this out.

## Agreed scope (from issue history)

- The blocker, bpmn-io/properties-panel#202 (tooltip support in the properties
  panel), is closed — no longer blocking.
- Autocompletion for variables *after* they're mapped is owned by
  bpmn-io/properties-panel / bpmn-js-element-templates, and a separate
  architecture discussion is evaluating moving output mapping to the engine
  entirely. Neither is in scope here.
- What *is* actionable in this repo: render the first (or designated default)
  `@DataExample` of a connector's `outputDataClass` as a tooltip on the
  `Result expression` property, starting with the REST and Webhook connectors,
  per the team's Feb 2025 architecture decision ("start simple by fetching the
  first data example ... and render it inside the tooltip").

## Mechanism

### 1. `DataExample` gets a default id constant

`element-template-generator/annotations/.../DataExample.java`:

```java
public @interface DataExample {
  String DEFAULT_ID = "default";

  String id() default DEFAULT_ID;

  String feel() default "";
}
```

Connector authors can leave `id()` unset to mark an example as canonical, or
override it (as `HttpCommonResult`'s existing `id = "basic"` and the four
`AgentResponse` examples already do) when they need distinct, named examples
for docs generation. This is a non-breaking change: no doc template currently
relies on the old empty-string default id (verified against `README.peb` and
`AI_AGENT.peb`, which key `exampleData` by explicit ids like `"basic"`,
`"text"`, `"json"`, `"assistantMessage"`, `"withToolCalls"`).

### 2. Resolve the "primary" example, preserving declaration order

In `ClassBasedDocsGenerator`, extract the per-method reflection/FEEL-evaluation
logic currently inlined in `collectExampleData` into a private helper, so it
can be reused by a new method:

```java
public static Optional<DataExampleModel> resolvePrimaryExampleData(Class<?> type) {
  var models = findAllDataExampleMethods(type).stream()
      .map(ClassBasedDocsGenerator::buildDataExampleModel)
      .toList();
  return models.stream()
      .filter(m -> DataExample.DEFAULT_ID.equals(m.id()))
      .findFirst()
      .or(() -> models.stream().findFirst());
}
```

`collectExampleData` (used today only for markdown docs generation) keeps its
existing signature and behavior, now implemented in terms of the same shared
per-method builder — no functional change for docs.

### 3. Wire the resolved example into the `resultExpression` tooltip

In `ClassBasedTemplateGenerator.addServiceProperties`, compute the primary
example once (if `template.outputDataClass() != Void.class`), before branching
on Outbound vs. Inbound, and format it into a tooltip string, e.g.:

```
Example response:
<pre>{escaped example.json()}</pre>
Example FEEL expression: <code>{example.feel()}</code> → <code>{example.feelResultJson()}</code>
```

(`feel`/`feelResultJson` are only included when the example declares a `feel`
expression, since it's optional on `@DataExample`.) HTML special characters in
the JSON are escaped manually (`&`, `<`, `>`) — no new dependency needed, no
existing HTML-escaping utility exists in this module, and example JSON is
authored by connector maintainers, not external input.

The resulting string is passed down to:
- `CommonProperties.resultExpression(String value, String exampleTooltip)` —
  new overload; sets `.tooltip(...)` when `exampleTooltip` is non-blank,
  leaving the existing `.description(...)` (doc link) untouched.
- `PropertyGroup.outputGroupOutbound(...)` / `outputGroupInbound(...)` — the
  existing `OUTPUT_GROUP_OUTBOUND` / `OUTPUT_GROUP_INBOUND` `BiFunction` fields
  become static methods taking a third `resultExpressionExampleTooltip`
  parameter, so both call sites (`ClassBasedTemplateGenerator`, and
  `HttpOutboundElementTemplateBuilder` for OpenAPI-generated connectors, which
  passes `null` — see Out of scope) compile against the new arity.

### 4. Wire the REST and Webhook connectors

- **REST** (`HttpCommonResult`): already has one `@DataExample(id = "basic", ...)`
  — works immediately via the "first one if no default id" fallback; no code
  change required.
- **Webhook** (`HttpWebhookExecutable`): currently declares no `outputDataClass`.
  Add `outputDataClass = WebhookResultContext.class` to its `@ElementTemplate`
  annotation, and a `@DataExample` static method on `WebhookResultContext`
  producing a representative `request`/`connectorData` example.

## Out of scope

- `HttpOutboundElementTemplateBuilder` (`http-dsl` module) — used for
  OpenAPI-generated multi-operation connectors, which have no single Java
  output class to annotate with `@DataExample`. Its `OUTPUT_GROUP_OUTBOUND`
  call site passes `null` for the new tooltip parameter; behavior unchanged.
- Any properties-panel / bpmn-js-element-templates UI work.
- Autocompletion of mapped variables after the `Result expression` (separate,
  cross-repo effort; also being reconsidered as part of the engine-level
  output-mapping discussion).

## Testing

- `OutboundClassBasedTemplateGeneratorTest` / `InboundClassBasedTemplateGeneratorTest`:
  assert the `resultExpression` property's tooltip is populated for a fixture
  class with `@DataExample`, and that connectors without `outputDataClass` /
  `@DataExample` keep the current tooltip-less behavior (no regression).
- A small unit test for `resolvePrimaryExampleData` covering: default-id
  example present among several → picked; no default-id example → first one
  (declaration order) picked; no examples → empty.
- Existing docs-generation tests (`OutboundClassBasedDocsGeneratorTest`) should
  continue to pass unchanged, confirming `collectExampleData`'s behavior is
  preserved by the refactor.
