# Route v1 agent requests through native providers

* Deciders: Agentic AI Team
* Date: Aug 21, 2026

## Status

**Accepted**. Delivered across a short PR sequence: the v1-to-native provider configuration mapping,
followed by removal of the LangChain4j integration. A transitional configuration flag exists only to
sequence those PRs and is removed together with LangChain4j.

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
changing the v1 wire contract, and we want to converge on a single provider stack rather than
maintain two indefinitely. Should we keep both stacks as they are, or route v1 requests through the
native providers and retire LangChain4j?

## Decision Drivers

* **Feature ceiling**: native providers surface vendor capabilities (structured reasoning, prompt
  caching, effort) that the LangChain4j abstraction cannot express.
* **v1 wire compatibility**: existing v1 element templates, job worker types, and process variables
  must keep working byte-for-byte — the migration must be invisible to deployed processes.
* **Behavior identity per provider**: a native provider calls the same vendor API the corresponding
  LangChain4j integration wrapped, so per-provider behavior is preserved.
* **Single stack**: two execution stacks behind one handler have no path to convergence and double
  the long-term maintenance surface; the module should end up on one.

## Considered Options

1. Keep both stacks unchanged — v1 requests resolve LangChain4j factories, v2 requests resolve
   native factories.
2. Translate the v1 provider configuration to native at the v1 request boundary, then remove
   LangChain4j.

## Decision Outcome

Chosen option: **Option 2 — translate v1 provider configuration to native at the request boundary and
remove LangChain4j**, because it lifts the capability ceiling on the native path, preserves the v1
wire contract through an internal translation layer, and converges the module onto a single provider
stack.

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

* **D3 — LangChain4j is removed, not retained as a fallback.** The v1 functions route through the
  native providers unconditionally; the LangChain4j framework binding, factories, and
  `dev.langchain4j` dependencies are removed as part of this decision, once the translation is in
  place. Delivery is split across a short sequence of PRs for reviewable change size; a transitional
  configuration flag exists only to sequence those PRs and carries no lasting meaning.

* **D4 — Configurations a native provider cannot represent are mapped faithfully or fail loud, never
  silently downgraded.** The v1 OpenAI-compatible provider allows an optional (blank) API key, with
  authentication carried in custom headers; the native custom-endpoint authentication requires a
  credential because the OpenAI SDK mandates one to build a client. A missing key is therefore mapped
  to a placeholder value, with the real authentication carried in request headers — behavior
  identical to v1 — rather than rejected. A configuration with no faithful native representation
  fails loud at translation time.

### Non-goals

* A model **capability matrix** — a separate follow-on, as noted in ADR 009.
* Any change to the **v1 wire shape** — element templates, job worker types, and process variables
  are unchanged; the translation layer is internal.

### Positive Consequences

* v1 jobs run on the native path, carrying provider capabilities beyond the LangChain4j ceiling.
* The v1 wire contract is preserved by an internal translation layer.
* Per-provider behavior is preserved, since the native provider calls the same vendor API.
* The module converges onto a single provider stack, dropping the `dev.langchain4j` dependency
  surface.

### Negative Consequences

* A permanent v1-to-native translation shim is retained for as long as the v1 element templates are
  supported, so the v1 wire shape lives on in code even after its execution engine is removed.
* Some v1 configurations require a faithful-but-indirect mapping (e.g. a placeholder credential for
  header-based auth) to preserve behavior, adding translation-layer edge cases to maintain.

## Pros and Cons of the Options

### Option 1: Keep both stacks unchanged

* Good, because it requires no migration work.
* Bad, because v1 jobs stay capped at the LangChain4j capability ceiling.
* Bad, because two stacks are maintained behind one handler with no path toward convergence.

### Option 2: Translate at the request boundary and remove LangChain4j (chosen)

* Good, because v1 jobs gain the native capability set while the v1 wire contract is preserved by an
  internal translation layer.
* Good, because per-provider behavior is identity-preserving (native calls the same vendor API).
* Good, because it converges to a single provider stack and drops the `dev.langchain4j` surface.
* Bad, because there is no runtime fallback to LangChain4j once it is removed if the native routing
  misbehaves for some v1 configuration.
