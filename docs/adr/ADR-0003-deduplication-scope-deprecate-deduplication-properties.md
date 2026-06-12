# ADR-0003: Deduplication Scope and Deprecation of deduplicationProperties

## Status
Accepted

## Context
Inbound deduplication collapses process elements that share a deduplication ID into a single
executable. Two mechanisms decide which properties form that ID:

- `@InboundConnector.deduplicationProperties` — an inclusion *allow-list*: when set, only the
  named properties contribute to the ID;
- `PROPERTIES_EXCLUDED_FROM_DEDUPLICATION` — a *deny-list* used by the default path: all
  properties except the runtime-managed ones.

The allow-list silently leaves every *other bound* property out of the deduplication scope while
it stays bound to the shared executable model. When elements collapse to one executable but
differ in such a property, the executable retains an arbitrary value (whichever element won the
grouping) — the general form of the webhook bug fixed in
[#6684](https://github.com/camunda/connectors/issues/6684). No connector in this repository sets
the allow-list, and it is half-wired: the update-compatibility check always compares the
deny-list scope even when the ID was computed from the allow-list.

## Decision
Adopt the invariant **a property excluded from deduplication must not be bound to the shared
model** — declare it template-only ([ADR-0002](ADR-0002-template-only-properties.md)) and resolve
it per request. Standardize on the deny-list as the single deduplication scope and deprecate the
allow-list:

- **Phase 1 (non-breaking, now):** annotate `@InboundConnector.deduplicationProperties` with
  `@Deprecated(forRemoval = true)`, deprecate the `CONNECTOR_<name>_DEDUPLICATION_PROPERTIES`
  environment variable, and log a runtime warning when either is used. Behavior is unchanged.
- **Phase 2 (a future major):** remove the annotation element and the allow-list code path so the
  deny-list is the only deduplication model.
- **Phase 3 (optional):** add a build-time guard that flags a bound property excluded from
  deduplication, turning the invariant into a compile-time guarantee.

## Consequences

### Positive
- Removes a foot-gun that silently produces an arbitrary value in a deduplicated executable.
- Leaves a single deduplication model (the deny-list) instead of two divergent ones; removal also
  resolves the latent update-compatibility inconsistency.
- Pairs with `TemplateOnly` to give an explicit, correct way to keep a property out of dedup.

### Negative
- Deprecating public API and an environment variable requires a multi-release migration window
  for out-of-repo consumers.
- Eventual removal broadens the deduplication scope for anyone who set a narrow subset, producing
  more executables (though it fixes the correctness problem).
- Connector authors must now reason explicitly about the bound / deduplicated / template-only
  distinction.
