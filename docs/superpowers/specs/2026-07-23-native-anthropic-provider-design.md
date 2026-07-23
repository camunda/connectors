# Native Anthropic Provider — Design

**Epic:** #7211 (own the LLM layer). **Date:** 2026-07-23.

**Goal:** Bring a native Anthropic Messages chat-model provider live on the v2 own-LLM-layer path, at
feature parity with today's langchain4j (L4J) Anthropic path **plus** reasoning/effort and prompt caching —
without server tools, Skills, or the capability matrix. Ships as a PR stacked on the v2-config-types PR
(`agentic-ai/v2-config-types`).

## 1. Scope

**In:**
- Two backends: `anthropic-api` (direct, `api.anthropic.com`, `x-api-key`) and `compatible` (arbitrary
  base URL + `apiKey`/`None` auth + custom headers/query/body params).
- Native Anthropic Messages runtime (NOT L4J): request/response converters, transport factory, the
  `ChatModelApi` implementation and its factory — with the factory **registered** (the v2-config-types PR
  left factories unregistered).
- Capabilities exercised: text, tool calling, multimodal **user-message input**, reasoning/effort, prompt
  caching.
- Generated v2 element templates showing the Anthropic provider + its backend field groups.
- Unit + e2e tests for the shipped behaviour only; the real-API smoke IT (Anthropic rows).

**Out (deferred, each its own follow-up):**
- Cloud backends: `bedrock-mantle`, `claude-platform-aws`, `azure-foundry`, `vertex` (each = one auth
  block; per-auth-family PRs).
- OAuth 2.0 on the `compatible` backend (net-new; see §10).
- Server tools (web_search / web_fetch / code_execution), Skills.
- Inline-native `tool_result` multimodal routing (belongs with the capability matrix).
- The capability matrix / `ModelCapabilities` / resolver (deferred wholesale; not on the landing branch).

## 2. Architecture context

The provider-config UX design defines three decoupled layers: **presentation presets** (the sealed-union
`type` discriminator = Modeler dropdown entry), **transports/backends** (endpoint + auth), and **wire
mappers** (internal converters). This PR delivers the Anthropic preset at layer 3, with exactly two of its
backends wired, and the AnthropicMessages wire mapper. Adding the remaining backends later widens the
backend sub-union and `@JsonSubTypes` — additive, no interface/SPI change.

## 3. Configuration model

`AnthropicChatModelConfiguration` is added as a member of the v2 `ProviderConfiguration` sealed union
(bare type name, package `io.camunda.connector.agenticai.aiagent.model.request.v2`). It widens the union's
`permits` clause and `@JsonSubTypes`.

- **Backend sub-union** with two members this PR:
  - `anthropic-api` — direct endpoint; `x-api-key` from a required API-key field.
  - `compatible` — required base URL; `CompatibleAuthentication` = `None | ApiKey` (OAuth omitted this PR);
    optional custom headers / query params / body params.
- **`api` field** — enum, single value (`messages`) this PR; hidden in the template when single-valued;
  additive/BC (a future Anthropic non-Messages API just adds a value).
- **Reasoning/effort** — the thinking union + effort output-config the pilot already models
  (`ThinkingMode`, `AnthropicEffort` enums), provider-agnostic core + opaque provider bag.
- **Prompt caching** — the opt-in toggle (top-level `cache_control`) the pilot wired.

**Labels (Modeler):** provider dropdown = **"Anthropic"** (no "(Claude)" suffix); connection dropdown =
**"Anthropic API"** (`anthropic-api`) / **"Custom / compatible endpoint"** (`compatible`).

**Backend id decision:** the pair is `anthropic-api` + `compatible` (mixed; not `direct`+`compatible`).
The capability-matrix resolver keys on backends via a plain string compare, so a per-provider id needs no
shared cross-provider vocabulary. Label ≠ id; the dropdown never shows the id.

## 4. Runtime

Port from the pilot's native Anthropic implementation, adapting names to the current rename scheme (§12):
- `AnthropicChatModelApi` (implements the `ChatModelApi` SPI), its `AnthropicChatModelApiFactory`, and
  **register the factory** in autoconfiguration.
- `AnthropicMessageRequestConverter` (domain messages → Anthropic `MessageCreateParams`) and the response
  converter (Anthropic response → domain `AssistantMessage` / continuation), including the content-filter
  and truncation error mappings the pilot added.
  - **`pause_turn` handling is included** (kept, not deferred): the response converter maps a
    `pause_turn` stop reason to a `ChatModelResult.Continuation` (and `mapStopReason` returns `null` for
    it), feeding the existing continuation loop; unknown future stop reasons degrade to
    `UnknownStopReason` rather than throwing. This is ~4 provider-neutral lines intrinsic to the converter
    with no server-tools coupling in the code. `pause_turn` in practice only fires during long
    server-tool turns (deferred), so it is effectively dormant here, but keeping it is free defensive
    correctness — the rebuild must not strip it.
- `AnthropicOkHttpClientFactory` — builds the vendor OkHttp client per backend: direct (`apiKey`) vs
  compatible (base URL + `apiKey`/none sentinel + static header/query/body maps).
- Execution model stays synchronous `Completed | Continuation` (the existing SPI shape).

## 5. Capabilities & multimodal (deferred subsystem — parity via existing extraction)

The capability subsystem (`ModelCapabilities`, `ModelCapabilitiesResolver`, `CapabilityMatrix`,
`CapabilityAwareToolCallResultStrategy`) is **not on the landing branch** and is **not** introduced here.
Native Anthropic supplies **no** `ModelCapabilities` object; there is no `capabilities()` consumer to
satisfy (verify — §11).

- **Multimodal user input** (images/PDFs in a user message) is rendered as native Anthropic image/document
  blocks by the request converter — same as L4J today.
- **Tool-call-result documents** ride the branch's existing **unconditional `<doc/>` extraction** (the
  ADR-004 pre-capability behaviour: strip the document from the tool result, re-insert as a synthetic
  `UserMessage` tagged with the tool-call reference, which then renders as a native block). Identical to
  L4J today. Inline-native `tool_result` multimodal is the deferred capability-matrix feature.

## 6. Parity bar & acceptance criteria

Target = **L4J Anthropic parity + reasoning/effort + prompt caching, with no user-facing regression.**
The three pilot features beyond L4J (server tools, Skills, inline tool_result multimodal) do not exist in
L4J either, so deferring them is "not yet ahead," not a regression.

Two hard acceptance criteria (must be verified, not assumed):
1. **Full parameter parity** — every parameter the v1/L4J Anthropic template exposes today (temperature,
   topP, maxTokens, stop sequences, etc.) is mapped by the native converter. A missed parameter is a real
   regression.
2. **Tool-result documents** behave exactly as today (existing `<doc/>` extraction; no capability gate).

## 7. Element templates

Regenerate the v2 templates via `mvn clean process-classes` (the generator binds `process-classes`, not
`compile`). The Anthropic provider appears in the dropdown with its backend groups and fields; the `api`
field is hidden while single-valued.

## 8. Testing

Port tests for **shipped behaviour only**; leave deferred-feature tests on the pilot to cherry-pick when
the feature lands (keeps this PR green with no test asserting unshipped behaviour).

| Test area | This PR |
|---|---|
| Basic tool calling | include |
| Reasoning/effort | include |
| Multimodal user input | include |
| Prompt caching | include |
| Error mapping (content-filter, truncation) | include |
| Wire-format smoke (deterministic/WireMock) | include |
| Tool-call-**result** routing | defer |
| Skills / code-execution / web_search / web_fetch | defer |

**Real-API smoke IT** (the pilot's cross-provider acceptance IT — confirm its current name;
memory had it as `NativeProviderAcceptanceIT`, referred to in discussion as `RealProviderApiSmokeIT`):
ship the whole IT **minus** OpenAI rows/params (required for compilation — OpenAI config types are not in
this PR), with server-tool rows `@Disabled` (with a reason; the server-tools PR re-enables them). It stays
real-API env-gated — **not run in CI, and not run locally without explicit per-run authorization.** It
lives in the separate `connectors-e2e-test-agentic-ai` module (cross-module build required after any
model/serialization change).

## 9. Backend feature note

Both backends this PR are Anthropic-operated / arbitrary (full-feature-capable), so no operator-ceiling
gating is needed yet. Ceiling gating (capped cloud backends) arrives with the cloud-backend PRs and the
capability matrix.

## 10. Deferred: OAuth 2.0 on `compatible`

Compatible auth ships as **API key / None** only. OAuth 2.0 is deferred to a follow-up. Rationale:
- OAuth is net-new for LLM provider auth (not in the L4J Anthropic path), i.e. beyond the parity bar.
- Reusable infra exists but not in agentic-ai: `OAuthTokenCache` (Caffeine, SHA-256-keyed, TTL from
  `expires_in`, thread-safe) lives in `connector-commons/http-client`, wired only by the generic REST
  connector and App Integrations. The MCP client rolls its **own** single-slot cache
  (`OAuthHeadersSupplier`, using `OAuthService` only to build the request) — a pre-existing gap that
  should migrate to `OAuthTokenCache`.
- The vendor SDK client is built per job-worker execution (short-lived — seconds, ~one LLM call unless a
  continuation), so a token fetched at client-build time comfortably outlives the client; there is **no**
  intra-execution staleness blocker. `OAuthTokenCache`'s value here is **cross-execution** token sharing
  (reuse a token across the many short-lived per-execution clients/continuations rather than re-fetching
  each job).

**Follow-up PR:** add `CompatibleOAuthAuthentication` (mirroring MCP's `OAuthAuthentication`:
tokenEndpoint / clientId / clientSecret / audience / clientAuthentication / scopes), reuse `OAuthService`
+ `OAuthTokenCache`, fetch the token before `AnthropicOkHttpClientFactory` builds the client and inject it
via `builder.putHeader("Authorization", "Bearer …")` (same path as the static `compatible.headers()`
map), and ideally migrate MCP's hand-rolled cache onto `OAuthTokenCache` in the same PR.

## 11. Open verifications (agent, before finalizing the plan)

1. The `ChatModelApi` SPI on `agentic-ai/v2-config-types` has **no** `capabilities()` method to implement
   (capabilities deferred).
2. The unconditional `<doc/>` tool-result extraction is present on that branch.
3. The exact v1 Anthropic parameter surface to map for acceptance criterion §6.1.
4. The structure delta between the reference branch's Anthropic config and the target shape (§3, §12).

## 12. Build / branch / agent logistics

- **Stacks on** `agentic-ai/v2-config-types` (not directly on the SPI PR). New dedicated worktree; Sonnet
  agent via subagent-driven development; stops before any push.
- **Reference branch is a GUIDE ONLY — do NOT cherry-pick.** `agentic-ai/v2-config-types-bak-with-providers`
  (its last two commits carry the Anthropic + OpenAI v2 config). Its structure and package naming have
  diverged from the target, so cherry-pick will not apply. The agent **rebuilds the Anthropic config by
  hand** to match §3 and the rename scheme below. The implementation plan must spell the target structure
  out in full.
- **Rename scheme** (from the in-flight rename/prep work the PR stacks on): bare `ProviderConfiguration` in
  the versioned package (no `V2` prefix on the type); `Ai` prefix dropped in favour of `Agent`; the `V1`/
  `V2` suffix survives only on connector `Function` + `Request` classes. Connector type-ID strings are the
  external contract and stay untouched.
- **Build constraints:** never use `-am` (rewrites proprietary license headers to Apache across the
  reactor); regenerate templates with `mvn clean process-classes`; cross-compile/run the separate
  `connectors-e2e-test-agentic-ai` module after model/serialization changes.
