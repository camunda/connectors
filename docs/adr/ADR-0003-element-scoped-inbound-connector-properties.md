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

- A connector may model itself with more than one input class. `@ElementTemplate.inputDataClass` is
  a `Class<?>[]`: the generator merges the `@TemplateProperty` fields of every listed class into the
  *same* element template, so the modeling experience is unchanged. This is purely an
  element-template concern and carries no runtime semantics.
- The deduplication scope is declared separately, as a runtime concern, via
  `@InboundConnector.deduplicationClasses`. Only the properties contributed by those classes are
  taken into account for deduplication; everything else bound into the same template (the
  element-scoped classes) is excluded. Keeping the two annotations independent preserves the rule
  that element-template annotations carry only generation concerns and runtime annotations carry only
  runtime concerns. (The pre-existing string-list `@InboundConnector.deduplicationProperties` is
  deprecated in favour of the class-based form.)
- The runtime turns `deduplicationClasses` into a set of property-key *prefixes* by introspecting the
  classes' Jackson serialization paths (`DeduplicationPropertyResolver`). It walks plain bean types
  and stops at scalars, containers and polymorphic (`@JsonTypeInfo`) types, so a single prefix such
  as `inbound.auth` transparently covers every nested/sealed subtype key without enumerating
  subtypes. A raw property is in scope iff its key equals, or is nested under, a prefix. This needs
  only Jackson (already used for binding), so the runtime gains **no dependency on the
  element-template-generator**. The same scope drives both the deduplication id and the
  divergence guard, so they stay consistent.
- Element-scoped properties are therefore **excluded from deduplication** (elements differing only in
  them still group) and are **bound per activated element at correlation time**, not once at
  activation.
- Binding lives on the SDK contract: `CorrelationResult.Success#bindProperties(Class)` delegates to
  `ProcessElement#bindProperties(Class)`. The runtime attaches a binder (carrying the
  secret-replacement + FEEL pipeline) to the activated element of a successful correlation, so the
  SDK declares the contract while the runtime owns the implementation.
- The webhook connector applies this through its existing `WebhookResult#response()` function,
  which the runtime evaluates after correlation. Because the `WebhookResultContext` carries the
  correlation result, the function resolves the response from the element that actually matched
  (via `Success#bindProperties`). The response logic stays in the connector module (using its own
  element-scoped class), so the runtime controller stays connector-agnostic and there is a single
  response mechanism — no separate `respond(...)` method and no runtime-side duplicate of the
  response model. (`response()` remains the one webhook response API; connectors whose response is
  fixed at trigger time, such as Slack, use it the same way.)

## Consequences

### Positive
- Per-element correctness: deduplicated elements share one executable yet each resolves its own
  element-scoped values. No null-by-design field on the bound model.
- One element template spanning all input classes; `deduplicationClasses` membership defines the
  dedup scope, with no hardcoded per-connector keywords and no runtime→generator dependency. For the
  webhook the derived scope matches the previous deny-list output exactly, so deduplication ids are
  unchanged.
- The runtime is connector-agnostic — response construction stays in the connector's `response()`
  function, and the runtime no longer carries a webhook-specific binding type. A single response
  mechanism, with no `respond(...)` method to keep in sync.
- A reusable concept: any inbound connector with per-element runtime values can use it.

### Negative
- A second binding lifecycle for authors to understand (activation-time vs correlation-time).
- Element-scoped binding/evaluation runs **past the transaction boundary** (the process instance is
  already created or the message published), so failures cannot undo correlation and callers must
  handle them without reporting the event as unprocessed. This is documented on the binding methods.
- Prefix-based scope resolution assumes the bound property key matches the Jackson serialization
  path, which holds for all current connectors (the runtime binds raw properties via Jackson). A
  property bound under a name that differs from its Jackson path would not be matched.
