# Using Reusable Credentials in Element Templates

This is a practical how-to for adding a reusable-credential (`Configuration`) chooser to a
connector's element template — for humans and agents alike. It complements
[ADR-0004](adr/ADR-0004-configuration-templates-in-element-templates.md), which records *why* the
feature is shaped this way; this document is *how* to build on it correctly, distilled from
migrating REST, GraphQL, Polling, the AWS connector family (~14 connectors + idp-extraction +
aws-sqs), and JDBC.

If you're adding a credential chooser to a connector that doesn't have one yet, read this end to
end before writing code — most of the mistakes below are easy to make and easy to avoid once you
know they exist.

## The shape: chooser + fallback + effective value

A connector that accepts a reusable credential needs three things, not one:

1. **The chooser** — a `@TemplateProperty(type = Configuration, ...)` field typed as your
   `@Configuration` class. Modeler renders it as a picker.
2. **The inline fallback** — the connector's existing inline fields, still present for a
   Camunda developer who doesn't want to set up a reusable credential for a one-off call. Hidden
   once a credential is chosen, via a `PropertyCondition.IsEmpty` condition on the chooser field.
3. **An effective-value accessor** — the connector must still read *one* resolved value. Override
   the getter (class-based model) or the record accessor (record-based model) to prefer the bound
   credential and fall back to the inline field:

   ```java
   // GraphQLRequest (record) — accessor override, not a new synthetic method, so every
   // existing caller of authentication() gets the effective value automatically.
   public Authentication authentication() {
     return authenticationConfiguration != null
         ? authenticationConfiguration.authentication()
         : authentication;
   }
   ```

   ```java
   // JdbcRequest (record) — same pattern for a scalar field.
   public SupportedDatabase database() {
     return configuration != null ? configuration.database() : database;
   }
   ```

   ```java
   // HttpCommonRequest (class) — the getter, not the field, carries the override.
   @Override
   public String getUrl() {
     String inline = super.getUrl();
     return inline != null && !inline.isBlank() ? inline : authenticationConfiguration != null
         ? authenticationConfiguration.url()
         : null;
   }
   ```

   This is the "per-connector merge" approach ADR-0004 resolved on ([Open questions
   (b)](adr/ADR-0004-configuration-templates-in-element-templates.md#open-questions)) — the ETG
   does not merge configuration and inline values for you.

## Field ordering: the chooser must render first

`ConditionPropertyOrderRule` requires a condition's referenced property to appear earlier in the
generated `properties[]` array. The chooser gates the fallback fields, so it must be declared (or
otherwise positioned) before them — this is also the right UX: pick a credential before falling
back to inline fields.

For a flat request this is just declaration order. For a **nested** field — a scalar bound inside
a sub-object rendered elsewhere, like GraphQL's `graphql.url` inside the `graphql` component — the
nested object is emitted as one contiguous block wherever it's declared, so a same-group sibling
declared after it (`urlOverride`, bound to the same underlying input) can end up separated from it
by unrelated fields in the same group once Modeler groups properties by tab. If the two are meant
to occupy the same visual slot, reorder the nested object's own fields so the gated one is last in
its group, and keep any framework-injected properties (e.g. `@DocumentReturnFormat`'s synthetic
fields) off that nested type so nothing trails after it before the sibling renders. Verify by
regenerating and listing the actual `properties[]` order for that group — don't assume from the
Java source alone.

## Chooser-only field vs. chooser + inline override

Not every fallback field needs an "override the credential's value" companion field. Decide based
on whether the property is **independent of the rest of the credential's data**, or **structurally
tied to it**:

- **REST auth's `url`** gets an inline `urlOverride` field, shown once a credential is bound. A
  Basic/Bearer/API-key credential's stored URL is just "the endpoint it happened to be created
  against" — the same secret is often valid against other endpoints (different paths, different
  environments) — so letting a task override the URL lets one credential serve many call sites.
  OAuth credentials skip the URL field entirely for the mirror-image reason: a token endpoint is
  inherently reused across many resource URLs, so there's nothing to override.
- **JDBC's `database` (engine)** gets *no* override once a credential is bound — it's simply
  hidden. The database engine dictates the JDBC driver and URL scheme paired with that specific
  host/port/credentials; overriding just the engine while keeping the credential's connection
  details would produce a connection string for the wrong driver against the wrong server.

Rule of thumb: if the same credential is plausibly reusable across different values of this field,
add the override. If varying this field independently of the rest of the credential would produce
a broken combination, don't.

## Validation pitfalls

Jakarta Bean Validation and Modeler's client-side constraints don't know about "required only when
visible" — they see one field, and if you leave it wired up naively, one of two ways it fails once
a credential can supply the value:

- **Move requiredness off the raw field, onto the effective value.** `@NotBlank`/`@NotNull` on the
  inline field would reject a legitimately-absent value once a credential supplies it. Replace it
  with an `@AssertTrue` that checks the accessor from the section above, with a message that names
  **both** possible sources:

  ```java
  @AssertTrue(message = "No URL provided by the credential or the element template")
  public boolean isUrlPresent() {
    return getUrl() != null && !getUrl().isBlank();
  }
  ```

  A bare `"URL is required"` doesn't tell the reader where to provide one — this bit us in exactly
  that form until it was fixed.

- **A `@Pattern` regex must accept the empty string if the field is genuinely optional.**
  Jakarta's `@Pattern` treats `null` as valid but *does* run the regex against `""`. Modeler's
  client-side `constraints.pattern` has the same problem — if your pattern is
  `^(https?://).*$`, an empty, optional field fails validation in the properties panel even
  though `notEmpty` correctly says it's not required. Add an empty-string alternative:
  `^($|https?://).*$`.

- **Normalize blank to `null` at the boundary**, not inside validation logic. A hidden field's
  Modeler-emitted value is an empty string, not an absent key — normalize it where the value is
  set (a record's compact constructor, or a class's setter), so every downstream check (pattern,
  presence, effective-value fallback) sees a real `null` instead of having to special-case `""`
  itself:

  ```java
  public void setUrl(final String url) {
    this.url = (url == null || url.isBlank()) ? null : url;
  }
  ```

- **Give a hidden enum/dropdown field a `defaultValue`.** Modeler still writes *some* literal for
  a hidden `zeebe:input` bound to a Dropdown-typed property, even when nothing edits it — an
  unset default risks an empty string reaching an enum deserializer, which throws rather than
  binding to `null`. This doesn't matter functionally once a credential is bound (the accessor
  override ignores the inline value entirely), but it must still be a *parseable* literal.

## Wording the chooser's description

- Say what the user is choosing, not the implementation. `"Choose a reusable credential. When
  set, it is bound as a whole to the connector's 'x' input"` leaks a Java field name that means
  nothing to a low-code user. Prefer: `"Choose a reusable <domain> credential, or configure
  one-time <domain> parameters below."`
- Don't over-claim the protocol. `"REST authentication credential"` is accurate for the REST
  connector itself, but GraphQL and Polling share the same credential type without being
  REST-specific connectors — say `"reusable authentication credential"` for those, and reserve
  the protocol name for the connector it actually names.
- `"Optional. Overrides the URL of the selected credential"` should say **reusable** credential —
  the reader hasn't necessarily internalized that "credential" here always means the reusable
  kind, since the same word could describe the inline fields it's contrasted with.

## Grouping

Don't give a field its own tab just because it existed at the top level before the credential
chooser did. If a field conceptually belongs with the rest of the connection/credential setup —
JDBC's database-engine dropdown, previously its own "Database" group — fold it into that group
(`propertyGroups` on `@ElementTemplate`, and the field's own `group` attribute) rather than
leaving a needless extra tab for one field.

## Versioning

Three separate version numbers are in play; don't conflate them.

- **The connector's element-template `version`.** Bump once per released set of changes, not once
  per internal iteration — see [connector-template-versioning.md](connector-template-versioning.md)
  for the general rule. If you're still iterating within the same unreleased/unmerged PR, land the
  changes at the same version and don't leave behind a `versioned/` snapshot for a version that
  never shipped; regenerate so the archived snapshot is the one true prior *released* state
  (main's), and the live file is the one true next version.
- **The `@Configuration` class's own `version`.** This is independent of the connector's template
  version — it's the credential schema's own floor-semantics revision (see ADR-0004's `Decision`
  section). Bump it when the credential's shape changes in a way that would make an
  already-created instance invalid against the new schema (e.g. adding a new mandatory field) —
  *only* if the prior version was actually released; folding an unreleased iteration into the same
  version applies here too.
- **`engineVersion` must be `^8.10` or newer.** The `Configuration` property type, the chooser,
  and the cluster-variable-backed credential store are an 8.10+ capability — every element
  template using this feature must declare at least `engineVersion = "^8.10"` on its
  `@ElementTemplate`. Check this explicitly when migrating an existing connector: an older
  connector may still be declaring an earlier floor from before it had any reason to bump it.

## Testing checklist

For every property that can come from a credential or an inline field, cover:

- [ ] Credential bound, no inline value — the credential's value is used.
- [ ] No credential, inline value present — the inline value is used.
- [ ] Both present — the credential wins, *except* for a chooser + inline **override** field (see
      "Chooser-only field vs. chooser + inline override" above, e.g. REST auth's `urlOverride`),
      where the inline value intentionally takes precedence once set (verify via the actual
      accessor, not just via `ConnectionHelper`-style resolution helpers if the connector has
      both).
- [ ] Neither present, and the credential doesn't (or can't) supply the value — binding fails
      with a message naming both possible sources.
- [ ] The full JSON → Jackson-binding path, not just direct object construction — a
      Modeler-generated diagram can carry unconditional leftover defaults (e.g. a nested
      discriminator's default value) for a field the user never touched, and only the real
      deserialization path exercises that.

Never hand-edit the generated `element-templates/*.json` (or the embedded `configurationTemplates`
block) to test these — regenerate via the connector's `GenerateElementTemplate` test class or a
module build (`mvn test-compile`), then diff. The generator embeds serialization details (enum
casing, an `elementTemplateVersion` display value mirroring the numeric version, per-group
property ordering) that are easy to get subtly wrong by hand and won't be caught by an eyeball
review of the JSON.

## Trying it out locally

A reusable credential is just a cluster variable with a `metadata` bag, so you can create one
against a local running cluster without Hub or a Modeler UI:

```bash
# "value" holds the @Configuration class's fields verbatim, e.g. authentication + url.
curl -X POST http://localhost:8088/v2/cluster-variables/global \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "myCredential",
    "metadata": {
      "kind": "CREDENTIAL",
      "configurationTemplate": "<the @Configuration id, e.g. io.camunda.connectors:rest-authentication:1>",
      "configurationTemplateVersion": 1,
      "displayName": "My Credential (demo)"
    },
    "value": { "authentication": { "type": "bearer", "token": "..." }, "url": "https://example.com" }
  }'
```

Reference it from a BPMN model with `=camunda.vars.env.myCredential` on the chooser's bound input.
The engine stores and returns the value verbatim — nothing credential-specific is engine-side, so
this works today even where Hub/Modeler's own chooser UI for a given template isn't wired up yet.

## See also

- [ADR-0004: Configuration Templates in Element Templates](adr/ADR-0004-configuration-templates-in-element-templates.md)
  — the design decision this guide builds on.
- [connector-template-versioning.md](connector-template-versioning.md) — the general
  element-template versioning rules referenced above.
- `AGENTS.md` → Connector implementation patterns — points here.
