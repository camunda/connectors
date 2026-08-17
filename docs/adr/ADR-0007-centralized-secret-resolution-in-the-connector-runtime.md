# ADR-0007: Centralized Secret Resolution in the Connector Runtime

## Status
Accepted

## Context

[PDP-3040](https://github.com/camunda/product-hub/issues/3040) adds a new way to write secrets in a process: `camunda.secrets.<name>`. Values come from secret stores configured on the broker, and two new endpoints read them: `POST /v2/secrets/resolve` and `POST /v2/secrets/list`. In time this is meant to replace the way the Connector Runtime handles `{{secrets.X}}` and bare `secrets.X` today. [#8222](https://github.com/camunda/connectors/issues/8222) tracks the work on the connectors side.

Eleven constraints shape the decision.

1. **For jobs, the engine resolves these secrets itself.** It replaces the placeholder in a job's variables before the job is marked activated. Today that covers jobs a worker asks for; `camunda/camunda#56564` extends it to jobs the broker pushes, targeted at the same release. Job push was named in the epic's own phase-1 criteria from the start — long polling simply shipped first.
2. **Nothing resolves them anywhere else.** Inbound connectors have no job, and out-of-band configuration validation runs outside any process, so neither is covered now or after that work lands.
3. **`camunda.secrets.<name>` is a FEEL expression; `{{secrets.X}}` is text the engine ignores.** In an input mapping a deployment is rejected if the reference is written as a plain value or inside quotes — the authoring form is the bare path `=camunda.secrets.NAME`, which the engine evaluates into the placeholder text and substitutes later (but see §7 for how far that rule reaches). The two forms are therefore not interchangeable in a model, and anything that generates input mappings has to know which it is emitting.
4. **The runtime replaces secrets one name at a time, and the new endpoint wants them in groups.** Replacement runs over the request as serialized JSON, before it is bound to objects, calling a single-name lookup for each match. The resolve endpoint takes a batch of references and caps how many one call may carry. So treating the cluster as one more `SecretProvider` would mean one HTTP request per secret, and the existing `SecretProvider.fetchAll` cannot help: it returns values without saying which name each belongs to.
5. **The resolve call carries no tenant** — the cluster derives it from the caller's token. The only routing question is therefore which `CamundaClient` to call, not how to describe a tenant.
6. **The runtime's pattern for the old form overlaps the new one**, matching the `secrets.<name>` part inside `camunda.secrets.<name>`. The same method that finds those names also builds the outbound allow-list of declared secrets, so any change to what it matches changes what that allow-list permits.
7. **The engine's rule that a reference must be written as an expression covers input mappings only.** `SecretReferenceLiteralValidator` is registered for `ZeebeInput`, so it rejects a deployment where `camunda.secrets.<name>` appears as a string literal in a `zeebe:input` source. Inbound connectors do not use input mappings: their configuration lives in `zeebe:property` extension elements, read as raw text. Nothing validates, evaluates or resolves those. On the inbound surface the bare form is therefore not *permitted* — it is merely unpoliced, and any contract there is ours to define.
8. **The cluster's expression-evaluation endpoint does not know the new form.** The context it evaluates against registers `camunda.vars` and `camunda.processInstance` and nothing else. The engine's placeholder-emitting context — which makes `camunda.secrets.<name>` evaluate to its own reference text rather than to nothing — is reached only through `ExpressionProcessor.withSecretReferenceContext()`, whose sole caller is input-mapping evaluation. An expression containing the new form, sent to that endpoint, resolves against no such variable.
9. **A cluster variable can carry references inside its value.** A cluster variable has a kind: `JSON` or `SECRET_REFERENCE`. A `SECRET_REFERENCE` one holds `camunda.secrets.<name>` text instead of a value, and the engine swaps in the real value at job activation. It only does that for variables of that kind — a `JSON` one is left alone even if its contents look like a reference. Read through the expression endpoint, the stored text comes back as-is, so a reference can reach the runtime as *data* that no model wrote.
10. **The runtime can read a variable's kind, so it can apply the same rule.** `POST /v2/cluster-variables/search` returns `kind` for each variable, and the filter takes both a list of names and a kind. So one call can ask for exactly the `SECRET_REFERENCE` variables among a set of names. Verified against 8.10.0-SNAPSHOT. What the API does *not* return is the list of references the engine already scanned and stored, so the runtime has to find them in the value itself.
11. **Without a rule of some kind, any text that looks like a reference would be resolved.** Verified against the same cluster: a `JSON` variable holding `{"note": "camunda.secrets.NOT_DECLARED"}` read back exactly that text through the expression endpoint. Anything scanning evaluated results for references would resolve a secret nobody declared.

## Decision

Support `camunda.secrets.<name>` **for inbound connectors and configuration validation only**, resolving the references found in a request together rather than one at a time, and keeping the resolution separate from the existing replacement. Leave job handling to the engine. The two forms of secret stay completely apart, and the public SDK does not change.

1. **Do not handle the outbound job path.** The engine owns job activation and is closing its remaining gap there in the same release (Context §1). Building our own would put two components in charge of the same thing, with different failure behaviour, for as long as it took. Adding the outbound path later, if that work slips, is a small change on top of what this decision already builds; removing it afterwards would mean deleting code other things had come to depend on. Inbound and configuration validation are the cases nothing else will ever cover (Context §2), and they are what this addresses.

   **Accepted risk.** Every Connectors bundle asks the broker to push jobs, which is the case the engine does not yet cover. If that upstream work misses the release, secrets in the new form will not work for outbound connectors until it does.

2. **Keep the two forms apart.** `camunda.secrets.<name>` is read only through the cluster's resolve endpoint; `{{secrets.X}}` and bare `secrets.X` are read only through the runtime's existing secret providers. Neither falls back to the other when it comes up empty. They draw on different configuration — stores configured on the broker, against providers configured in the runtime — and letting an environment variable satisfy a reference written against a broker store would make it impossible to say where a secret actually came from.

3. **Resolve in groups, and only when there is something to resolve.** All references in one request are collected and resolved in as few calls as the endpoint's limit allows, rather than one at a time (Context §4). A request containing none of them does not call the cluster at all. That second point is what keeps this free for everything that does not use the new form, and it is also what would make the outbound path inert wherever the engine has already done the work — so if we ever add it, it disables itself rather than competing.

4. **Keep the new resolver inside the runtime; do not extend the public SDK.** Because the two forms are kept apart, the resolver is never one of the secret providers, so widening the `SecretProvider` interface would add public API that nothing here uses. `SecretProvider` and `fetchAll` are left exactly as they are, and existing implementations — including other people's — keep working unchanged.

5. **Route by client, not by tenant** (Context §5). Inbound connector contexts are already created per physical tenant, so each already holds the correct client; configuration validation already keeps per-tenant collaborators and gains one more. No new way of describing tenancy is introduced, and in particular the existing secret-resolution and secret-filtering context types are not merged.

6. **Treat every failure the same way, and log what it was.** All the failures the endpoint can report mean one thing to us: no value, so the connector fails exactly as it already does for a missing secret. There is no behaviour that branches on the reported cause. Because the endpoint answers successfully even when individual references fail, a missing permission would otherwise be indistinguishable from a misspelled name, so the reported cause is logged. A network failure, or a cluster too old to have the endpoint, is treated identically — such a cluster degrades to "secret not available" rather than failing outright.

7. **A refused secret and a missing one stay different outcomes.** The runtime already distinguishes them: a name the secret filter refuses is left in the text untouched, while a name it allows but nobody can supply fails the connector. The new form keeps both. Collapsing them would turn a deliberate refusal into a failure.

8. **Keep the two forms from overlapping in text, narrowly.** The old pattern is changed so it can no longer match inside a reference of the new form (Context §6), and only for that prefix — the pattern still matches in other places it arguably should not, and changing that is a separate question. Because the same method feeds the outbound allow-list, it is also taught to recognise the new form, so that the set of secrets that list permits is unchanged for every existing process.

9. **Do nothing yet about hiding secrets from error output.** Values in the new form can only appear where this decision resolves them, and the only place the runtime hides secrets from errors is on the outbound path, which this does not touch (Context §2 and the scope above). Two gaps are recorded as separate work rather than addressed here: inbound has never hidden secrets from error output, and once the engine substitutes a value into job variables the reference is gone, leaving nothing for the outbound masking to recognise — which already applies today and needs information the job does not currently carry, so it belongs upstream.

10. **The old form keeps working by default, with an explicit off switch.** Set `camunda.connector.secret-resolver.legacy.enabled` to `false` to disable resolution of `{{secrets.X}}` and bare `secrets.X`. This setting does not affect `camunda.secrets.<name>`, which uses the separate cluster-backed resolver.

11. **On inbound, a reference is only recognised as a whole property value written as an expression:** `=camunda.secrets.NAME`. That is the form the engine mandates wherever it polices the syntax (Context §3, §7), so inbound follows the same convention rather than inventing a second one. The leading `=` is consumed and the property replaced by the value; the property is *not* evaluated as FEEL, because FEEL evaluation on inbound is opt-in per field via `@FEEL` and several connectors' credential fields are plain strings — RabbitMQ's and the email connector's among them — so routing this through the FEEL path would silently do nothing for exactly the fields that hold secrets.

   Embedded text is not a supported form. A reference occurring inside a longer value, or without the leading `=`, is left untouched. Consequently nothing scans arbitrary property text for references, and a value that merely happens to contain a reference-shaped string is never resolved — the injection-safety property the engine gets from parsing the FEEL AST, obtained here by narrowing what counts as a reference instead.

12. **A reference that arrives as data is resolved only if something declared it.** A `SECRET_REFERENCE` cluster variable carries reference text in a value that no property wrote, and that text does not exist until the cluster has answered. So resolution also runs on the result of expression evaluation. It does not resolve whatever it happens to find there. Before anything is evaluated, the runtime builds an allow-list of the references it is willing to resolve:

   - every reference written in the raw property values, and
   - every reference inside a `SECRET_REFERENCE` cluster variable that those raw values name.

   The second part costs one call. The `camunda.vars.<scope>.<name>` occurrences in the raw values give the variable names; a single search filtered by those names and by kind returns just the variables allowed to declare secrets (Context §10), and their values are scanned for references.

   Anything else that looks like a reference is left alone (Context §11). The allow-list only ever permits, so missing something is safe: a reference we failed to find is a secret that does not resolve and a connector that fails visibly. It can never produce a wrong value.

   The values themselves are still substituted by expression evaluation, not by the runtime. Reading a variable here only decides what may be resolved. That keeps expression evaluation the one thing that interprets expressions: no scope rules to copy, no field paths to walk, and no risk of a variable's contents being read as part of an expression.

   Configuration validation has no raw properties. It evaluates a `credentialRef` and reads secrets only out of what comes back, so its allow-list comes from the cluster-variable half alone.

13. **A reference mixed into a larger expression is rejected, for now.** `="Bearer " + camunda.secrets.TOKEN` cannot be resolved before evaluation without splicing a secret value into expression text, where a quote or an operator in the value would silently change what the expression means; and it cannot be resolved after evaluation, because the endpoint returns nothing for it (Context §8). It fails with a message naming the supported form rather than resolving to something surprising. If the engine installs its placeholder context on the expression endpoint (see *Asks on the engine*), these become ordinary cases of Decision §12 and the restriction lifts — as does the need for Decision §11's special handling of the `=` form.

14. **Do the extra work once, not for every event.** Which references and which cluster variables a set of properties names is fixed for as long as the element is registered, so that scan runs once rather than on every event. Only the contents of the `SECRET_REFERENCE` variables can change while a connector runs, so only the part of the allow-list derived from them is fetched again, and it is cached the same way the outbound allow-list already is (`ProcessDefinitionSecretKeyCache`, keyed per physical tenant and process definition). Properties naming no secret and no cluster variable cost nothing: no call, no cache lookup. This matters because the element-scoped binding path runs for every correlated event, not once per connector.

**Asks on the engine.** None of these block the decisions above; each either removes work here or turns something we depend on into a guarantee.

- **Expose the secret references the engine already scanned and stored** on a cluster variable. Decision §12 has to find them in the value with a regex; the engine found them on the parsed expression and kept them. Reading those instead would make detection authoritative rather than approximate. Small, and not needed for any of this to work.
- **Extend the literal validation of Context §7 to `zeebe:property`.** The new syntax is not yet in use anywhere, so rejecting the unpoliced bare form costs no existing deployment today and will cost one later. The runtime enforces Decision §11 on its own regardless; this only moves the failure from first activation to deployment, which is where it belongs.
- **Install the placeholder context on the expression endpoint.** This is the one ask that removes code here rather than adding a guarantee, collapsing Decision §13 and most of §11 into §12. It changes the endpoint's answer for an expression naming the new form, and discloses nothing the caller did not write itself.

**Not covered here.** The hybrid Connector Runtime keeps resolving secrets locally in this release, as the epic decided, since routing it through the orchestration cluster would defeat the point of keeping secrets self-hosted. Restricting which secrets an inbound connector may read stays with [#7730](https://github.com/camunda/connectors/issues/7730). Note the asymmetry this creates, deliberately: the allow-list of Decision §12 restricts the new form, while `{{secrets.X}}` and bare `secrets.X` stay unrestricted on inbound as they are today. The new form is the one being defined here, so it gets the rule now; bringing the old form under one is that issue's job. Making element templates emit the new form (Context §3) is separate work.

## Consequences

### Positive

- Secrets in the new form work for inbound connectors and configuration validation, which nothing else covers and no engine change will.
- No code is written that is expected to become dead. The engine keeps sole ownership of job activation.
- Resolution costs one round trip per request rather than one per secret.
- The public SDK is unchanged, so third-party secret providers keep compiling and behaving as before.
- The two forms no longer overlap in text, and the set of secrets the outbound allow-list permits is unchanged for existing processes.
- Runtimes that do not use the new form are unaffected: no extra calls, no extra delay.
- If the outbound path does need covering later, it is a small addition on top of this, not a redesign.
- Inbound and the job path take the reference in the same shape, so what a modeller writes does not depend on which kind of connector reads it.
- Because Decision §11 matches a whole value rather than text anywhere, no property value is scanned for reference-shaped substrings, and a secret cannot be pulled in by a value that merely looks like a reference.
- Secrets held in `SECRET_REFERENCE` cluster variables work for configuration validation and inbound alike, which is what the credentials work needs.
- The runtime applies the same rule as the engine about which cluster variables may hold secret references, so the two agree on what resolves. No engine change is needed for that.
- Only declared references resolve. Text that merely looks like a reference does not, wherever it came from.
- The extra work is one call, cached, and skipped entirely for properties that name no secret and no cluster variable.

### Negative

- **Secrets in the new form do not work for outbound connectors until the engine's own job-push work ships.** This is the deliberate bet in Decision §1; if it slips, we cover that path after all.
- **A reference in the new form cannot be satisfied locally.** Keeping the forms apart means no environment variable will do, so local development and tests need either a configured store or a stand-in for the cluster.
- **A missing permission reads as "secret not available".** Treating all failures alike is far less code, at the cost of a misleading message; the log naming the real cause makes it findable but does not fix it. The runtime's account has to be granted permission to reveal secrets when upgrading, which needs documenting for Helm and Console.
- **Secret values resolved into inbound properties are not hidden from error output**, because inbound never hid them. This does not make that worse, but it does make the gap reachable by a second route.
- **There are now two ways to write a secret, and both stay.** They differ in syntax, in where values come from, in how they are configured and in how they fail. Maintainers have to know why they deliberately do not mix, and users have to know which one their store is set up for.
- **We depend on an unfinished API.** The client commands and both endpoints are marked experimental and open to change, so a client update could break this. Treating failures as described in Decision §6 bounds the worst case to secrets not resolving, rather than the runtime failing to start.
- **A secret whose value begins with `=`, resolved into a `@FEEL` field, is evaluated as an expression.** The FEEL deserializer for regular properties fires on any string value, not only on one starting with `=`, so a resolved value in that shape is parsed rather than used. Resolved values have to be marked opaque, or resolved after binding for those fields; either way this is a property of the replacement, not of the new form, and it applies to the legacy form today.
- **The allow-list is read from secondary storage, so it lags.** Cluster variables are read from the exported projection, not from the broker. A reference added to a `SECRET_REFERENCE` variable is not allow-listed until the export catches up, and the cache of Decision §14 adds its own delay on top. Until then the secret does not resolve. Safe and self-correcting, but visible, so it needs saying rather than discovering.
- **A runtime without secondary storage cannot build that half of the allow-list**, so secrets held in cluster variables will not resolve there. Secrets written directly in properties still do.
- **A truncated cluster-variable value hides references.** The search API reports truncation, and a reference past the cut is simply not found, so that secret does not resolve. It fails closed, but silently.
- **A reference mixed into an expression fails rather than resolving** (Decision §13). The message names the supported form, but a modeller who reasonably expects string concatenation to work has to restructure the model, and the restriction is invisible until the connector runs.

## Implementation status

Decisions §1–§10 are implemented in [#8299](https://github.com/camunda/connectors/pull/8299).

Decisions §11–§14 are ratified here and being built now. Until that lands, the code in that pull request still resolves the bare embedded form that Decision §11 drops, and has no pass and no allow-list for Decision §12. Tracked under [#8222](https://github.com/camunda/connectors/issues/8222).

Neither remaining *Ask on the engine* gates any of this. The earlier ask about reading a variable's kind is dropped: Context §10 establishes the runtime can already read it.
