# ADR-0001: Annotation-Driven Element Template Generation

## Status
Accepted

## Context
Camunda Modeler requires element templates — JSON descriptor files — for each connector so users can configure connector properties in the modeling UI. These descriptors must stay in sync with the connector's Java input model: property names, types, validation constraints, groups, and FEEL-mode flags.

Maintaining these JSON files by hand alongside Java code introduced drift: properties were added to Java models but the template was not updated, or vice versa. Reviewing JSON diffs in PRs was error-prone and required deep knowledge of the template schema.

## Decision
Generate element templates automatically from the Java connector class and its input model at build time using a dedicated `element-template-generator` module. Connector authors annotate their input model with `@ElementTemplate`, `@TemplateProperty`, `@PropertyGroup`, and validation annotations (`@NotEmpty`, `@Pattern`, etc.). A `GenerateElementTemplate` test class (and a corresponding Maven plugin) reads these annotations via reflection and produces the JSON descriptor.

The generated JSON is committed to the repository under `element-templates/` inside each connector module and symlinked into Camunda Modeler's resources directory for local development.

## Consequences

### Positive
- Element templates are always in sync with the Java model; drift is structurally prevented.
- Connector authors work only in Java; no manual JSON authoring or schema knowledge required.
- Validation constraints (Jakarta Bean Validation) double as template constraints with no extra work.
- Template changes are surfaced as Java annotation diffs, which are easier to review than raw JSON diffs.
- The generator is versioned with the SDK, so improvements to the template format propagate automatically.

### Negative
- Connector authors must learn a set of generator-specific annotations (`@TemplateProperty`, `@FEEL`, `@TemplateDiscriminatorProperty`, etc.) in addition to standard Java/Jakarta annotations.
- Advanced template features not yet modeled by annotations require workarounds or manual post-processing.
- Generated JSON is committed to the repo, creating noise in diffs when the generator format changes across SDK versions.
- The generator is a build-time dependency; issues in it can block all connector builds simultaneously.
