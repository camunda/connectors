# ADR-0004: Configuration Templates in Element Templates

## Status
Accepted

## Context
The Credentials initiative ([PDP-3396](https://github.com/camunda/product-hub/issues/3396)) introduces reusable, org-managed **credential instances** that replace auth fields inlined into element templates today. The full design lives in [camunda/connections-design](https://github.com/camunda/connections-design).

The design separates two layers:

- **Configuration** — the generic, domain-agnostic core-modeling substrate: the `Configuration` element-template property type, the configuration chooser, the configuration-template document format, and the runtime binding. All of this is credential-agnostic.
- **Credential** — the domain concept delivered on top: a configuration template whose `kind` is `CREDENTIAL`. Hub, personas, and the Modeler chooser UI stay credential-centric.

This ADR covers the **Configuration** layer as expressed in the connectors' Element Template Generator (ETG). A credential is just a configuration template with `kind = CREDENTIAL`.

A configuration instance is stored as a cluster variable (value + a generic `metadata` bag carrying `kind`, `configurationTemplate`, `configurationTemplateVersion`, `displayName`) and referenced from the BPMN model via a FEEL expression `=camunda.vars.env.<name>`.

For the connectors repository, an element template must be able to:
1. Declare a configuration **chooser** — a `{"type":"Configuration","configurationTemplate":…}` property the Modeler renders as a picker filtered to compatible configuration instances;
2. Embed the **configuration template** — the field definitions a configuration editor (Hub / Modeler) uses to render/validate a configuration of that type, under a `configurationTemplates` top-level key;
3. Stay non-breaking — existing inline-field templates and deployed processes keep working.

Element templates are already generated from the Java input model via annotations ([ADR-0001](ADR-0001-annotation-driven-element-template-generation.md)). This ADR specifies how configuration templates and chooser bindings are expressed in that model.

### How a chosen configuration reaches the connector

This is the point where the design changed materially, and it shapes this ADR.

An **earlier** version of the design required "connector-code invariance": the configuration schema's field names mirrored the connector's `zeebe:input` target paths, and the element template **deconstructed** the chosen configuration into those paths using hidden FEEL derefs (`@TemplateProperty(type = Hidden, defaultValue = "=credential.authentication")`). The connector received an identical job-variable shape and its Java code never changed. A previous revision of this ADR built the whole ETG design around that idiom, with AWS (whole-object derefs) and Kafka (cherry-picked field) as reference cases.

That approach was **dropped** ([connections-design GAP-001](https://github.com/camunda/connections-design/blob/main/GAPS.md), after the [PR #23 discussion](https://github.com/camunda/connections-design/pull/23)). Mirroring each connector's field layout hard-wires the configuration's shape to that layout — tight coupling that defeats the core credential benefit: a configuration bound as one object can later swap its authentication mechanism (basic → OAuth, key → token) without the consuming model changing. It also pushed per-connector deconstruction plumbing into every element template.

The **current** model is whole-object binding:

- The chosen configuration is bound **once, as a whole**, to the connector's dedicated **`configuration` input** — a channel distinct from the inline fields, so a bound configuration and any inline override/fallback can coexist:
  - Outbound: `<zeebe:input source="=camunda.vars.env.<name>" target="configuration" />`
  - Inbound: `<zeebe:property name="configuration" value="=camunda.vars.env.<name>" />`
- The **connector reads the `configuration` object as a whole** and interprets its structure itself (including which authentication subtype it carries). The configuration's shape is decoupled from the connector's internal field layout.
- There is **no deconstruction** in the element template — no hidden derefs, no per-connector fan-out.

Resolving "use the configuration or the inline value" (precedence: configuration → inline fallback) is **out of scope for the ETG** — it happens on the connector-consumption side, via a per-connector accessor override (see [Open questions](#open-questions) (b)).

## Decision
Extend the element-template generator with configuration support that reuses the existing annotation/DSL/generator machinery. New API surface:

1. **`@Configuration` (type-level annotation, in the SDK)** — marks a data class as a configuration type: `id()` (the id, e.g. `io.camunda:aws-credential:1`), `version()` (the floor revision), `name()` (editor display name), `kind()` (the configuration class; defaults to `CREDENTIAL`, the only value defined today). Its `@TemplateProperty`-annotated fields define the embedded template's `properties`.

   **Placement & naming.** This annotation lives in the SDK (`connector-core`, `io.camunda.connector.api.annotation`), **not** in the element-template-generator annotations, because it is a runtime-readable *identity contract* — the connector runtime reads its `id`/`kind` to interpret a bound `configuration` object, and the generator reads the same annotation to emit the chooser and the embedded template. This is exactly the shape of the existing `@OutboundConnector`/`@InboundConnector` annotations (type-level identity, read by both the runtime and the generator), so it belongs in the same package. Consequently it is named `@Configuration`, not `@ConfigurationTemplate`: "template" denotes the *generator output artifact* (the embedded `configurationTemplates[]` document), which is an ETG concern — the annotation marks the *input concept*. Presentation-only annotations (`@TemplateProperty`, `@ElementTemplate`, `@DropdownItem`) stay in the generator. The reference direction is one-way: the generator may depend on the SDK annotation, never the reverse.

2. **`PropertyType.Configuration` (enum addition)** — a connector-input field typed as a `@Configuration` class and annotated `@TemplateProperty(type = Configuration)` emits the chooser property. The chooser references the configuration **by id** (`configurationTemplate`), derived from the field type's `@Configuration.id()`. The chooser carries **no version** — the floor lives on the embedded `configurationTemplates[]` entry, linked by id.

3. **DSL/JSON model:**
   - `ConfigurationProperty extends Property` — the chooser; emits `{type:"Configuration", configurationTemplate:<id>, label, group, binding}`.
   - top-level `ConfigurationTemplate` record `{id, kind, version, name, properties}` — mirrors the element-template top-level minus BPMN-only fields.
   - `ElementTemplate.configurationTemplates` collection, emitted under the `configurationTemplates` key.

4. **`@ElementTemplate.configurations()`** — references the `@Configuration` classes whose templates to embed. (The member is named for the input concept; the emitted top-level key stays `configurationTemplates`, the output artifact.)

**Whole-object chooser binding.** The chooser is a single property bound once to one `zeebe:input` (outbound) / `zeebe:property` (inbound). On selection the Modeler writes the whole-configuration FEEL expression (`=camunda.vars.env.<name>`) plus cached `modelerConfigurationTemplate` / `modelerConfigurationName` metadata to that target. The connector reads the bound object as a whole. This is uniform across all connectors — there is no deconstruction and no per-connector variation on the element-template side. A connector that wants a configuration therefore exposes a single field typed as its `@Configuration` class:

```java
@TemplateProperty(type = Configuration, group = "connection",
                  binding = @PropertyBinding(name = "configuration"))
JdbcConnection connection;
```

**Configuration-template property shape.** Per connections-design `CONFIGURATION_TEMPLATE.md`, properties inside an embedded configuration template differ from ordinary element-template properties:
- the binding is **`{"type":"property","name":<key>}`** — `name` is the key in the configuration's value object, and may be a **dotted path** (e.g. `authentication.accessKey`) to nest the value — never `zeebe:input`/`zeebe:property`. This uses a dedicated `property` `PropertyBinding` type in the DSL.
- **`feel` is not emitted** — configuration values are atomic literals or secret references, never FEEL.
- **`type: "Configuration"` is disallowed** — a configuration cannot reference another configuration.
- a **`secret: true`** hint may mark secret-bearing fields (editor rendering hint, not enforced), via a `secret` attribute on `@TemplateProperty`, surfaced only on configuration-template properties.

The generator walks `@Configuration` classes in a dedicated *configuration-template extraction mode* (`property` bindings, no `feel`, `secret` hint) — distinct from the `zeebe:input`/`zeebe:property` extraction used for the host element template. Nested objects produce dotted `property` binding names via the existing prefixing in the property walker.

### Example

Configuration definition — its `@TemplateProperty` fields become the embedded template's `properties` (emitted with `property` bindings, no `feel`, secrets marked `secret: true`). Note `@Configuration` is the SDK annotation; the `@TemplateProperty` fields it governs are the generator's:
```java
@Configuration(id = "io.camunda:aws-credential:1", version = 1, name = "AWS Credential")
public record AwsCredential(
    @TemplateProperty(group = "authentication") AwsAuthentication authentication,
    @TemplateProperty(group = "configuration")  AwsBaseConfiguration configuration) {}
```

Chooser field on the connector's request model (bound whole; the connector reads it):
```java
@TemplateProperty(type = Configuration, group = "authentication",
                  binding = @PropertyBinding(name = "configuration"))
AwsCredential configuration;

// on the connector class:
@ElementTemplate(/* … */ configurations = { AwsCredential.class })
```

Resulting template fragment (chooser + embedded template):
```json
"properties": [
  { "type": "Configuration", "configurationTemplate": "io.camunda:aws-credential:1",
    "binding": { "type": "zeebe:input", "name": "configuration" } }
],
"configurationTemplates": [
  { "id": "io.camunda:aws-credential:1", "kind": "CREDENTIAL", "version": 1, "name": "AWS Credential",
    "properties": [
      { "id": "accessKey", "type": "String", "secret": true,
        "binding": { "type": "property", "name": "authentication.accessKey" } }
    ] }
]
```

**Scope / non-goals** (per connections-design `GAPS.md`):
- Chooser-only; the inline **fallback** path (gated by the not-yet-implemented `isEmpty` condition) is deferred.
- The **connector-side consumption** of the `configuration` object (reading it as a whole, and the configuration → inline-fallback precedence) is **not** part of the ETG and not implemented here — see [Open questions](#open-questions) (b) for the resolved per-connector-merge approach.
- **`configuresCredential`** conditions and the cached **`modelerConfigurationTemplateVersion`** attribute are **dropped** (connections-design GAP-007); per-type `Configuration` property + `isEmpty: false` distinguishes which configuration is chosen.
- Runtime secret resolution (`SecretUtil`, PDP-3040) is deferred and provisional (GAP-005/006) — does not affect template generation.

## Open questions

**(b) Where the configuration/inline merge lives — resolved: per-connector merge.** The connector must read the bound `configuration` object and apply precedence (configuration → inline fallback). connections-design `CREDENTIAL_TRANSITION.md` requirement 2 asserts this should be a **generic capability provided once by the connector runtime / SDK** (in the bind layer — `bindVariables` / `bindProperties` — merging the `configuration` variable under the inline fields before deserialization, so business code sees one resolved request unchanged).

That is one option, not the only one. The decoupling win of GAP-001 comes solely from "consume as a whole + interpret yourself"; it does **not** depend on where the merge code lives. Alternatives considered:
- **Per-connector merge (chosen)** — each connector's request model gains a `configuration`/credential field and resolves configuration-vs-inline itself, by overriding the accessors the connector already calls (e.g. `getAuthentication()`, `getConfiguration()`). No runtime change; some duplication across connectors (mitigated for AWS by hoisting the field + overrides once into the shared `AwsBaseRequest`); but the merge stays visible in the request model a reader already opens, and it ships incrementally, one connector at a time, without a cross-cutting SDK change.
- **Shared SDK helper** — generic merge code connectors call explicitly (`ConfigurationResolver.merge(inline, configuration)`): shared logic, per-connector invocation, no invisible bind-layer transform. Rejected for this round: it adds an indirection layer for a merge that, in practice, is a one-line precedence check per connector — the shared-base hoist (AWS) already gets the deduplication benefit without it.
- **Generic runtime/SDK bind-layer merge** — the design doc's stated preference; zero connector change, uniform, but front-loads generalizing the merge (esp. explicitly-set detection) across all connectors, and makes the precedence invisible at the point business code reads the request. Rejected as too much complexity for what a human needs to understand in the request model; see the maintainability trade-off called out for this POC.

This choice does not block the ETG work: the element template emits the identical chooser + `configuration` binding regardless of where the merge lives.

## Consequences

### Positive
- Rides the established annotation-driven model ([ADR-0001](ADR-0001-annotation-driven-element-template-generation.md)); the new surface is small.
- **The element-template side is uniform** — one whole-configuration binding, never deconstruction — so every connector adopts the chooser the same way and no per-connector fan-out logic lives in templates.
- **Decoupled** — the configuration's shape is independent of the connector's field layout, so a configuration can evolve its authentication mechanism without changing the consuming model.
- The configuration is the connector's own data class, and the chooser's `configurationTemplate` and the embedded template's `id` derive from one `@Configuration`, so they always match.
- One `@Configuration` class can be shared across many connectors (AWS: 14 connectors, one `@Configuration`).
- Configuration-template properties reuse the element-template property model (groups, conditions, dropdowns), so editors and validators are shared — the design's stated reason for not inventing a bespoke format.

### Negative
- New generator surface beyond the annotations: a `property` `PropertyBinding` type, a `secret` attribute on `@TemplateProperty`, a `kind` field, and a configuration-template extraction mode (property bindings, no `feel`) distinct from the host-element extraction.
- The whole-object model shifts effort **onto the connector-consumption side** (reading the `configuration` object + precedence) — work the ETG does not do; see [Open questions](#open-questions) (b) for where that merge lives (resolved: per-connector).
- A connector that binds a `configuration` object and has its inline fields become chooser-driven produces a **chooser-only** template until the deferred inline-fallback mechanism (and the not-yet-implemented `isEmpty` condition) lands.

## Extension: OpenAPI / Postman generators ([#8113](https://github.com/camunda/connectors/issues/8113))

The annotation-driven path (this ADR's core decision) covers connectors whose element template is generated from a hand-written `@ElementTemplate`-annotated Java class, e.g. the REST/GraphQL/HTTP-polling connectors built on `io.camunda:http-json:1`. Until #8113, the two spec-driven generators that also target that same connector type — `OpenApiOutboundTemplateGenerator` and `PostmanCollectionOutboundTemplateGenerator` — were out of scope: they built their templates through `HttpOutboundElementTemplateBuilder`, which only ever emitted the legacy inline `authentication.*` `zeebe:input` properties, parsed and pre-filled per declared scheme. #8113 brings both generators into scope and makes the chooser their **default** output.

**What changed:**
- `HttpOutboundElementTemplateBuilder` now embeds the same shared credential referenced by the hand-authored REST template, `io.camunda.connectors:rest-authentication:1` (defined once, in `connector-http-base`, as a `@Configuration`-annotated `RestAuthenticationConfiguration`), rather than deriving a bespoke per-scheme configuration from the parsed spec. The embedded `ConfigurationTemplate` is produced by the same shared `ConfigurationTemplateUtil.fromAnnotatedClass` helper the annotation-driven path uses (extracted from `ClassBasedTemplateGenerator` as part of this change), so the two paths are structurally guaranteed to agree instead of relying on hand-kept parity.
- **Credential-only is the default**, not merely additive: the legacy inline `authentication.*` properties are no longer emitted unless a new opt-out, `GenerationFeature.LEGACY_INLINE_AUTHENTICATION`, is explicitly enabled. The flag defaults to absent (i.e. `false`), is threaded through both generators and the `congen-cli` (`--feature`), and is intended for removal once callers (Modeler/Hub) can universally consume configuration-template credentials — it is not a long-term feature toggle.
- **The chooser is required when the source declares mandatory authentication.** Unlike the always-optional chooser on the hand-authored REST template (which has an inline fallback), the generated chooser has no fallback once credential-only is the default, so it is marked `optional: false` with a `notEmpty` constraint whenever every operation's effective authentication requirement excludes "no auth" (mirroring how the legacy path computed per-operation effective auth: an operation's override when set, else the connector-wide list).

**Accepted trade-offs:**
1. **Spec-derived pre-fills are discarded.** The legacy path pre-filled the OAuth token endpoint/scopes and the API key name/location straight from the spec into inline fields. A reusable, spec-independent credential cannot be pre-filled this way. Mitigated by folding that detail (declared scheme(s), OAuth token endpoint/scopes, API key name/location) into the chooser property's `tooltip`, so it is not silently lost — just no longer auto-populated into an editable field.
2. **Per-operation authentication collapses to one credential.** A spec whose operations declare different auth schemes previously produced conditional, per-operation-group inline auth sections; the chooser is a single, unconditional property, so operation-level scheme variation is no longer distinguishable in the template UI (only surfaced, in aggregate, in the tooltip). Accepted for v1 in exchange for a single reusable credential per connector instance.

The legacy inline path (`PropertyUtil.authPropertyGroup`) is unchanged and remains reachable behind `LEGACY_INLINE_AUTHENTICATION` for callers that cannot yet consume configuration-template credentials; it is not part of this ADR's decision and is expected to be removed once that constraint no longer applies.
