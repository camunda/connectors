# ADR-0007: Centralized Secret Resolution in the Connector Runtime

## Status
Accepted

## Context

[PDP-3040](https://github.com/camunda/product-hub/issues/3040) adds a new way to write secrets in a process: `camunda.secrets.<name>`. Values come from secret stores configured on the broker, and two new endpoints read them: `POST /v2/secrets/resolve` and `POST /v2/secrets/list`. In time this is meant to replace the way the Connector Runtime handles `{{secrets.X}}` and bare `secrets.X` today. [#8222](https://github.com/camunda/connectors/issues/8222) tracks the work on the connectors side.

Six constraints shape the decision.

1. **For jobs, the engine resolves these secrets itself.** It replaces the placeholder in a job's variables before the job is marked activated. Today that covers jobs a worker asks for; `camunda/camunda#56564` extends it to jobs the broker pushes, targeted at the same release. Job push was named in the epic's own phase-1 criteria from the start — long polling simply shipped first.
2. **Nothing resolves them anywhere else.** Inbound connectors have no job, and out-of-band configuration validation runs outside any process, so neither is covered now or after that work lands.
3. **`camunda.secrets.<name>` is a FEEL expression; `{{secrets.X}}` is text the engine ignores.** A deployment is rejected if the reference is written as a plain value or inside quotes — the authoring form is the bare path `=camunda.secrets.NAME`, which the engine evaluates into the placeholder text and substitutes later. The two forms are therefore not interchangeable in a model, and anything that generates input mappings has to know which it is emitting.
4. **The runtime replaces secrets one name at a time, and the new endpoint wants them in groups.** Replacement runs over the request as serialized JSON, before it is bound to objects, calling a single-name lookup for each match. The resolve endpoint takes a batch of references and caps how many one call may carry. So treating the cluster as one more `SecretProvider` would mean one HTTP request per secret, and the existing `SecretProvider.fetchAll` cannot help: it returns values without saying which name each belongs to.
5. **The resolve call carries no tenant** — the cluster derives it from the caller's token. The only routing question is therefore which `CamundaClient` to call, not how to describe a tenant.
6. **The runtime's pattern for the old form overlaps the new one**, matching the `secrets.<name>` part inside `camunda.secrets.<name>`. The same method that finds those names also builds the outbound allow-list of declared secrets, so any change to what it matches changes what that allow-list permits.

## Decision

Support `camunda.secrets.<name>` **for inbound connectors and configuration validation only**, resolving all references found in a request together, before the existing replacement runs. Leave job handling to the engine. The two forms of secret stay completely apart, and the public SDK does not change.

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

**Not covered here.** The hybrid Connector Runtime keeps resolving secrets locally in this release, as the epic decided, since routing it through the orchestration cluster would defeat the point of keeping secrets self-hosted. Restricting which secrets an inbound connector may read stays with [#7730](https://github.com/camunda/connectors/issues/7730) — inbound applies no such restriction today and this does not add one. Making element templates emit the new form (Context §3) is separate work.

## Consequences

### Positive

- Secrets in the new form work for inbound connectors and configuration validation, which nothing else covers and no engine change will.
- No code is written that is expected to become dead. The engine keeps sole ownership of job activation.
- Resolution costs one round trip per request rather than one per secret.
- The public SDK is unchanged, so third-party secret providers keep compiling and behaving as before.
- The two forms no longer overlap in text, and the set of secrets the outbound allow-list permits is unchanged for existing processes.
- Runtimes that do not use the new form are unaffected: no extra calls, no extra delay.
- If the outbound path does need covering later, it is a small addition on top of this, not a redesign.

### Negative

- **Secrets in the new form do not work for outbound connectors until the engine's own job-push work ships.** This is the deliberate bet in Decision §1; if it slips, we cover that path after all.
- **A reference in the new form cannot be satisfied locally.** Keeping the forms apart means no environment variable will do, so local development and tests need either a configured store or a stand-in for the cluster.
- **A missing permission reads as "secret not available".** Treating all failures alike is far less code, at the cost of a misleading message; the log naming the real cause makes it findable but does not fix it. The runtime's account has to be granted permission to reveal secrets when upgrading, which needs documenting for Helm and Console.
- **Secret values resolved into inbound properties are not hidden from error output**, because inbound never hid them. This does not make that worse, but it does make the gap reachable by a second route.
- **There are now two ways to write a secret, and both stay.** They differ in syntax, in where values come from, in how they are configured and in how they fail. Maintainers have to know why they deliberately do not mix, and users have to know which one their store is set up for.
- **We depend on an unfinished API.** The client commands and both endpoints are marked experimental and open to change, so a client update could break this. Treating failures as described in Decision §6 bounds the worst case to secrets not resolving, rather than the runtime failing to start.
