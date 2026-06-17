# ADR-0003: Element-Scoped Properties for Inbound Connectors

## Status
Accepted

## Context
Inbound connectors deduplicate the BPMN elements that reference them: elements with the same
connector configuration collapse into a single executable, keyed by a deduplication id derived from
their properties (minus a set of runtime-managed keywords). The executable binds its properties once
at activation from that shared, deduplicated set.

This breaks down when a property may legitimately differ between the grouped elements yet must not
affect grouping. The motivating case is the webhook `responseExpression` (issue #6684): two start
events on the same webhook path that differ only in their response expression must still deduplicate
into one webhook registration, but each must produce *its own* response. With activation-time
binding, the collapsed executable captures whichever element won the grouping, so the "wrong"
response can be returned.

An earlier attempt (a `TemplateOnly` marker type, never merged) made such a property appear in the
element template but never bind to the model — the connector then fished the value out of the raw
element by string key. It worked but left a permanently-`null` field on the bound model and pushed
ad-hoc resolution into each connector and the runtime, which Pavel rejected as a leaky abstraction.

## Decision
Introduce **element-scoped properties** as a first-class concept, alongside the existing
(connector-scoped) properties.

- A connector may declare a second input class via `@ElementTemplate.elementInputDataClass`. Its
  `@TemplateProperty` fields are merged into the *same* element template as `inputDataClass`, so the
  modeling experience is unchanged.
- Element-scoped properties are **excluded from deduplication** (elements differing only in them
  still group) and are **bound per activated element at correlation time**, not once at activation.
- Binding lives on the SDK contract: `CorrelationResult.Success#bindProperties(Class)` delegates to
  `ProcessElement#bindProperties(Class)`. The runtime attaches a binder (carrying the
  secret-replacement + FEEL pipeline) to the activated element of a successful correlation, so the
  SDK declares the contract while the runtime owns the implementation.
- The webhook connector applies this through a new lifecycle method
  `WebhookConnectorExecutable#respond(WebhookResultContext)`, invoked by the runtime after
  correlation. It resolves the response from the element that actually matched the request. The
  webhook controller becomes generic: trigger → correlate → respond. `WebhookResult#response()` is
  deprecated (it is created before the activated element is known) and kept only as a fallback.

## Consequences

### Positive
- Per-element correctness: deduplicated elements share one executable yet each resolves its own
  element-scoped values. No null-by-design field on the bound model.
- One element template and one source class per scope; class membership defines dedup scope.
- The runtime is connector-agnostic again — response construction returns to the connector via
  `respond(...)`, and the runtime no longer carries a webhook-specific binding type.
- A reusable concept: any inbound connector with per-element runtime values can use it.

### Negative
- A second binding lifecycle for authors to understand (activation-time vs correlation-time).
- Element-scoped binding/evaluation runs **past the transaction boundary** (the process instance is
  already created or the message published), so failures cannot undo correlation and callers must
  handle them without reporting the event as unprocessed. This is documented on the binding methods.
- The deduplication exclusion of element-scoped keys is currently still expressed as hardcoded
  webhook keywords in `Keywords.PROPERTIES_EXCLUDED_FROM_DEDUPLICATION`. Deriving it from
  `elementInputDataClass` (a `@TemplateProperty`-aware "walker") is deferred to a follow-up, because
  it introduces a dependency from the runtime onto the element-template-generator and rewires the
  core dedup path — better reviewed in isolation.
- `WebhookResult#response()` is deprecated but not removed; the fallback remains until in-repo and
  external implementers migrate to `respond(...)`.
