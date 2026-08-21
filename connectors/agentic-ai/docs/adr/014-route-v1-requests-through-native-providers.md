# Route v1 agent requests through native providers

* Deciders: Agentic AI Team
* Date: Aug 21, 2026

## Status

**Accepted**. Realized in the PR that introduces the v1-to-native provider configuration mapping,
gated behind a configuration switch. The LangChain4j integration is retained as the alternate path
for now; its removal is a deferred follow-on.

## Context and Problem Statement

[ADR 009](009-chat-model-provider-spi.md) introduced a module-owned `ChatModel` provider SPI and
reshaped LangChain4j into per-provider factories behind it, naming native (non-LangChain4j)
providers as an explicit follow-on. Those native providers now exist for every vendor the v1
provider union supports: Anthropic, OpenAI (including the Azure / Microsoft Foundry backend),
Google Gemini (including the Vertex AI backend), and AWS Bedrock (Converse).

The module still ships two execution stacks. A v1 request binds the sealed
`request.v1.ProviderConfiguration`, which the LangChain4j factories `supports()`; a v2 request binds
a native `request.v2` configuration, which the native factories `supports()`. Both stacks flow
through the same request handler and the same `ChatModelRegistry` — they differ only in the provider
configuration subtree, because the request data model (prompts, memory, limits, events, response) is
shared and the handler and SPI are version-agnostic.

The LangChain4j path caps provider capabilities at LangChain4j's abstraction, which is the
constraint ADR 009 set out to escape. We want v1 jobs to run on the native providers without
changing the v1 wire contract, and without a big-bang removal of the LangChain4j stack while the
native routing is still proving out. Should we keep both stacks as they are, or route v1 requests
through the native providers?

## Decision Drivers

* **Feature ceiling**: native providers surface vendor capabilities (native server-side tools,
  structured reasoning, prompt caching, effort) that the LangChain4j abstraction cannot express.
* **v1 wire compatibility**: existing v1 element templates, job worker types, and process variables
  must keep working byte-for-byte — the migration must be invisible to deployed processes.
* **Behavior identity per provider**: a native provider calls the same vendor API the corresponding
  LangChain4j integration wrapped, so per-provider behavior is preserved.
* **Controlled rollout**: keep a runtime lever to fall back to the LangChain4j path while the native
  routing proves out, and avoid coupling the routing change to the larger LangChain4j removal.
* **Pre-GA freedom**: the v2 request types and native providers are not released, so the migration
  is not constrained by a released dual-path compatibility contract.

## Considered Options

1. Keep both stacks unchanged — v1 requests resolve LangChain4j factories, v2 requests resolve
   native factories.
2. Translate the v1 provider configuration to native at the v1 request boundary, gated behind a
   configuration switch, retaining LangChain4j as the alternate path; remove LangChain4j in a later
   change.
3. Translate at the request boundary and remove LangChain4j in the same change (no switch, no
   fallback).

## Decision Outcome

Chosen option: **Option 2 — translate v1 provider configuration to native at the request boundary,
behind a configuration switch, retaining LangChain4j for now**, because it lifts the capability
ceiling on the native path and preserves the v1 wire contract, while keeping a runtime fallback and
decoupling the routing change from the LangChain4j removal.

The decision comprises:

* **D1 — The migration seam is provider-configuration translation, not a second execution path.**
  Because a v1 and a v2 request differ only in the provider-configuration subtree, a v1 request is
  served by mapping its `request.v1.ProviderConfiguration` to the equivalent native `request.v2`
  configuration at the v1 connector function boundary, then reusing the existing native path
  unchanged. The handler, registry, native factories, and request data model are not modified.

* **D2 — v1 `bedrock` maps to the native Bedrock Converse provider, not to the Anthropic-over-Bedrock
  (Bedrock Mantle) backend.** Converse is the same Bedrock API the LangChain4j integration wrapped
  and serves every Bedrock foundation model, so it is the behavior-identical target for all v1
  Bedrock jobs. The Anthropic Bedrock Mantle backend remains a v2-only opt-in for callers who want
  Anthropic-native features over Bedrock.

* **D3 — Gate native routing behind a configuration switch; retain LangChain4j for now.** The v1
  functions route through the native providers, but a configuration switch selects the LangChain4j
  path, kept as a runtime fallback while the native routing proves out. The LangChain4j framework
  binding, factories, and `dev.langchain4j` dependencies are not removed in this change; their
  removal — and with it the switch, which has no meaning once the fallback is gone — is a deferred
  follow-on.

* **D4 — Configurations a native provider cannot represent are mapped faithfully or fail loud, never
  silently downgraded.** The v1 OpenAI-compatible provider allows an optional (blank) API key, with
  authentication carried in custom headers; the native custom-endpoint authentication requires a
  credential because the OpenAI SDK mandates one to build a client. A missing key is therefore mapped
  to a placeholder value, with the real authentication carried in request headers — behavior
  identical to v1 — rather than rejected. A configuration with no faithful native representation
  fails loud at translation time.

### Non-goals

* A model **capability matrix** — a separate follow-on, as noted in ADR 009.
* **LangChain4j removal** — deferred to a follow-on once native routing is proven; retained behind
  the switch here.
* Any change to the **v1 wire shape** — element templates, job worker types, and process variables
  are unchanged; the translation layer is internal.

### Positive Consequences

* v1 jobs run on the native path, carrying provider capabilities beyond the LangChain4j ceiling.
* The v1 wire contract is preserved by an internal translation layer.
* The configuration switch provides a runtime fallback during rollout.
* Per-provider behavior is preserved, since the native provider calls the same vendor API.

### Negative Consequences

* Two provider stacks and the `dev.langchain4j` dependency surface persist until the deferred
  removal.
* The switch is transitional and must be removed together with LangChain4j; left indefinitely it
  reintroduces dual-path maintenance.
* A permanent v1-to-native translation shim is retained for as long as the v1 element templates are
  supported, so the v1 wire shape lives on in code even after its execution engine is eventually
  removed.

## Pros and Cons of the Options

### Option 1: Keep both stacks unchanged

* Good, because it requires no migration work.
* Bad, because v1 jobs stay capped at the LangChain4j capability ceiling.
* Bad, because two stacks are maintained behind one handler with no path toward convergence.

### Option 2: Translate at the request boundary behind a switch, retain LangChain4j (chosen)

* Good, because v1 jobs gain the native capability set while the v1 wire contract is preserved by an
  internal translation layer.
* Good, because the switch gives a runtime rollback lever and decouples routing from the larger
  removal.
* Good, because per-provider behavior is identity-preserving (native calls the same vendor API).
* Bad, because LangChain4j, its dependency surface, and the switch are retained temporarily and must
  be removed together in a follow-on; left indefinitely they reintroduce dual-path maintenance.

### Option 3: Translate and remove LangChain4j in the same change

* Good, because it converges to a single provider stack immediately and drops the `dev.langchain4j`
  surface at once.
* Bad, because it couples the routing change to a large removal with no runtime fallback if the
  native routing misbehaves, offering no bake period even though one is cheap to keep.
