# ADR-0003: Credential Schemas in Element Templates

## Status
Proposed

## Context
The Credentials initiative ([PDP-3396](https://github.com/camunda/product-hub/issues/3396)) introduces reusable, org-managed **credential instances** that replace auth fields inlined into element templates today. A credential instance is stored as a cluster variable (value + a generic `metadata` bag carrying `kind=CREDENTIAL`, `schemaRef`, `schemaVersion`, `displayName`) and is referenced from the BPMN model via a FEEL expression `=camunda.vars.env.<name>`. The full design lives in the `connections-design` project.

For the connectors repository this means an element template must be able to:
1. **Declare a credential chooser** — a `{"type":"Credential","schemaRef":…,"version":…}` property the Modeler renders as a picker filtered to compatible credential instances;
2. **Embed the credential schema** — the field definitions an editor uses to render/validate a credential of that type;
3. **Stay non-breaking** — existing inline-field templates and deployed processes keep working; the connector's job-variable shape and Java code do not change.

Element templates are already generated from the Java input model via annotations ([ADR-0001](ADR-0001-annotation-driven-element-template-generation.md)). The open question was how to express credential schemas and chooser bindings in that same annotation-driven model, and — critically — how a single chosen credential is distributed onto a connector whose credential-bearing fields are spread across nested, partly per-task objects, without changing the connector.

## Decision
Extend the element-template generator with credential support that reuses the existing annotation/DSL/generator machinery as far as possible. The new API surface is four pieces:

1. **`@CredentialSchema` (type-level annotation)** — the only genuinely new annotation. Marks a data class as a credential schema: `id()` (the `schemaRef`), `version()` (floor-compatibility revision), `label()`. Its `@TemplateProperty`-annotated fields are walked by the *existing* `extractTemplatePropertiesFromType` machinery to produce the embedded schema's field list.

2. **`PropertyType.Credential` (enum addition)** — a connector-input field typed as a `@CredentialSchema` class and annotated `@TemplateProperty(type = Credential)` emits the chooser property. `schemaRef` and `version` are **derived from the field's type**, so the connector's own data class is the verbatim schema source.

3. **DSL/JSON model** — a new `CredentialProperty extends Property` (carrying `schemaRef` + `version`), a top-level `CredentialSchema` record `{id, version, label, properties}`, and a new `ElementTemplate.credentialSchemas` collection.

4. **`@ElementTemplate.credentialSchemas()`** — references the schema classes to embed in the template.

**Binding model.** The chooser is always a **single** property bound to one `zeebe:input`; on selection the Modeler writes the whole-credential FEEL expression (`=camunda.vars.env.<name>`) to that target. This honours the design's single-binding chooser contract.

**Deconstruction needs no new API.** When a connector consumes a credential's parts at different/nested variable paths, the credential is "fanned out" using the *existing* hidden-property idiom: each consumed field is annotated `@TemplateProperty(type = Hidden, defaultValue = "=<chooserVar>.<field>")`. This works because (a) an explicit `type` override turns off the generator's recursion (`TemplatePropertiesUtil.isContainerType`), so a complex/sealed field collapses to one hidden property; (b) `defaultValue` becomes the property `value`, which the Modeler writes as the `zeebe:input` **source**; (c) nested-path prefixing yields nested targets (e.g. a hidden field inside `KafkaTopic` produces target `topic.bootstrapServers`). This is the same pattern dozens of connectors already use for composed hidden inputs (Asana, GitHub, HubSpot, …).

This was validated against engine semantics: input mappings are evaluated **in order and a later source can read an earlier target**, so the static deconstruction inputs can dereference the chooser-populated `credential` variable; and writing two sub-paths into one parent object (e.g. credential-sourced `topic.bootstrapServers` alongside user-entered `topic.topicName`) is already how connectors like Kafka work today.

**Reference cases.** Three structural shapes were analysed and confirmed to fit:
- **JDBC** — credential is one whole object the connector already reads (`connection`); chooser binds straight onto it, no deconstruction.
- **AWS** — credential bundles two whole sibling objects (`authentication` + `configuration`) shared by all 14 AWS connectors via `AwsBaseRequest`; two hidden whole-object derefs feed the original top-level paths.
- **Kafka** — credential is a **flat composite** mirroring no single connector object (`{authentication, bootstrapServers}`), cherry-picking a scalar out of the co-mingled `topic` object while its sibling `topicName` stays an inline per-task field.

### Examples

Define a credential schema — its `@TemplateProperty` fields become the embedded schema:

```java
@CredentialSchema(id = "io.camunda:aws", version = 1)
public record AwsCredential(
    @TemplateProperty(group = "authentication") AwsAuthentication authentication,
    @TemplateProperty(group = "configuration")  AwsBaseConfiguration configuration) {}
```

**JDBC** — whole object, no deconstruction (the connector already reads `connection`):

```java
@CredentialSchema(id = "io.camunda:jdbc-connection", version = 1)
public sealed interface JdbcConnection permits UriConnection, DetailedConnection {}

// in JdbcRequest:
@TemplateProperty(type = Credential, group = "connection",
                  binding = @PropertyBinding(name = "connection"))
JdbcConnection connection;

// on the connector class:
@ElementTemplate(/* … */ credentialSchemas = { JdbcConnection.class })
```

**AWS** — whole-object deconstruction onto the original top-level paths (shared by all 14 AWS connectors via `AwsBaseRequest`):

```java
// chooser anchor:
@TemplateProperty(type = Credential, group = "authentication",
                  binding = @PropertyBinding(name = "credential"))
AwsCredential credential;
// fed by hidden derefs (connector reads these, unchanged):
@TemplateProperty(type = Hidden, defaultValue = "=credential.authentication")
AwsAuthentication authentication;
@TemplateProperty(type = Hidden, defaultValue = "=credential.configuration")
AwsBaseConfiguration configuration;
```

**Kafka** — flat composite schema, scalar cherry-picked out of a co-mingled object:

```java
@CredentialSchema(id = "io.camunda:kafka", version = 1)
public record KafkaCredential(
    @TemplateProperty(group = "authentication") KafkaAuthentication authentication,
    @TemplateProperty(group = "connection")     String bootstrapServers) {}

// KafkaConnectorRequest.credential:  @TemplateProperty(type = Credential, binding = @PropertyBinding(name = "credential"))
// KafkaConnectorRequest.authentication: @TemplateProperty(type = Hidden, defaultValue = "=credential.authentication")
// KafkaTopic.bootstrapServers:       @TemplateProperty(type = Hidden, defaultValue = "=credential.bootstrapServers")
//   -> nested-path prefix makes the target "topic.bootstrapServers"; sibling topicName stays an inline field
```

Resulting template fragment (AWS chooser + one hidden deref):

```json
{ "type": "Credential", "schemaRef": "io.camunda:aws", "version": 1,
  "binding": { "type": "zeebe:input", "name": "credential" } },
{ "type": "Hidden", "value": "=credential.authentication",
  "binding": { "type": "zeebe:input", "name": "authentication" } }
```

**Scope of the prototype.** Chooser-only; the inline **fallback** path (showing original fields when no credential is bound, gated by an `isEmpty`/`equals:""` condition) is deferred. `schemaRef` and `version` are kept as separate fields with floor semantics; the combined `io.camunda:auth-config:1` form seen in some design docs is treated as an inconsistency to reconcile upstream. `schemaRegistryUrl` (a scalar inside Kafka's *sealed* `schemaStrategy`) is out of scope as the hardest sub-case.

## Consequences

### Positive
- Credentials ride the established annotation-driven model ([ADR-0001](ADR-0001-annotation-driven-element-template-generation.md)); the new surface is minimal — one annotation, one enum value, and the DSL additions.
- **Deconstruction requires zero generator support** — it reuses the existing hidden-FEEL-input idiom, so cherry-picked, multi-object, and whole-object credentials are all expressed with the same primitives.
- **Non-breaking at the connector level**: connector code and the job-variable shape are unchanged because hidden derefs feed the original target paths; only the template (a new version) changes.
- The credential schema is the connector's own data class, and `schemaRef`/`version` derive from the field type — keeping the "Java model is the verbatim template source" property.
- One credential schema class can be shared across many connectors (AWS: 14 connectors, one `@CredentialSchema`).

### Negative
- The flat-schema → nested-target mapping is encoded implicitly in two places (where a hidden field sits = target; its `defaultValue` = source) and relies on credential field names matching consumed paths; the generator should validate that every `=credential.X` references a real schema field.
- Marking shared base fields (e.g. `AwsBaseRequest.authentication`/`configuration`) as `Hidden` makes the generated template **chooser-only**, replacing the inline template until the deferred fallback mechanism (and the not-yet-implemented `isEmpty` condition) lands.
- The intermediate `credential` job variable leaks into the connector's variables (harmless — connectors tolerate unknown properties, but it is extra noise).
- The schema is embedded into every template that uses it (duplication); a shared schema registry is planned but not part of this design.
- Runtime correctness still depends on engine work outside this repo: the batched secret-resolution endpoint ([PDP-3040](https://github.com/camunda/product-hub/issues/3040)) and care around nested-target dereferencing ([camunda#54946](https://github.com/camunda/camunda/issues/54946)) for CO-MINGLED connectors beyond the prototype's scope.

## References
- [camunda/connections-design](https://github.com/camunda/connections-design) — credential model, runtime binding, element-template chooser, versioning
- [PDP-3396](https://github.com/camunda/product-hub/issues/3396) — Credentials initiative; [PDP-3040](https://github.com/camunda/product-hub/issues/3040) — engine-side secret resolution
- [ADR-0001](ADR-0001-annotation-driven-element-template-generation.md) — Annotation-Driven Element Template Generation
- [camunda#54946](https://github.com/camunda/camunda/issues/54946) — nested-target input-mapping dereference edge case
