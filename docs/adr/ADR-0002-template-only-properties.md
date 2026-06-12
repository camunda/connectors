# ADR-0002: Template-Only Properties via the TemplateOnly Marker

## Status
Accepted

## Context
Element templates expose properties for the Camunda Modeler UI. Historically every
`@TemplateProperty` was also bound to the connector's Java input model and read at runtime.
But some properties are pure output or runtime concerns whose value must be resolved per
request from the *activated* process element — not from a shared connector instance. Because
inbound connectors deduplicate elements into a single executable, binding such a property to
the model means the executable holds whichever element's value won the grouping (the webhook
`responseExpression` bug, [#6684](https://github.com/camunda/connectors/issues/6684)). The
generator had no first-class way to express "appears in the template, never bound."

## Decision
Introduce an uninstantiable `TemplateOnly` marker type in the element-template-generator. A
`@TemplateProperty`-annotated field of type `TemplateOnly` is *template-only*: emitted into the
generated template but never bound to the model. The generator enforces at build time that such
a field

- is excluded from binding — declared `static` or annotated `@JsonIgnore`; and
- declares an explicit `@TemplateProperty(type = ...)`, since the marker carries no inferable
  property type.

The template type is owned by the annotation, never derived from the Java field type. The
runtime is responsible for resolving the corresponding value per request from the activated
element.

## Consequences

### Positive
- "No runtime value" is enforced by the type system — `null` is the only assignable value, so
  there is nothing meaningful to read even reflectively.
- Build-time checks reject a template-only field that is bound or lacks an explicit type, so the
  guarantee cannot be dropped by accident; usages are greppable.
- Declaring the field as a `@JsonIgnore`'d record component keeps it in its original position, so
  generated templates remain byte-identical (no display-order churn) when migrating a property.
- Gives connector authors a correct, explicit tool for per-request and output-only properties.

### Negative
- Adds a generator-specific concept that connector authors must learn.
- A `@JsonIgnore`'d record component leaves a vestigial accessor that always returns `null`.
- Correctness relies on two coupled requirements (the marker type plus exclusion from binding)
  enforced by the generator rather than by the language.
