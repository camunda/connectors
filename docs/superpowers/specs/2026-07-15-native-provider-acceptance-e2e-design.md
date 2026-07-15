# Native-Provider Real-API Acceptance IT — Design

**Status:** Approved (brainstorm), pending spec review
**Date:** 2026-07-15
**Epic:** [#7211 — AI Agent: Own the LLM layer](https://github.com/camunda/connectors/issues/7211), native vertical pilot
**Branch:** `agentic-ai/issue-7211-vertical-pilot`

## Purpose

Add a cross-provider, real-API end-to-end **safety net** for the native
own-LLM-layer path *before* building the native OpenAI integration. The test
exercises the connector against a real provider API through the full engine
(BPMN → element template → agentic loop → output variable) and asserts on
**observable behaviour only**, so the same scenarios port unchanged from
Anthropic today to OpenAI (and Anthropic-on-AWS) as those rows are added.

This test is the **acceptance harness** the OpenAI-parity build will be graded
against: "add OpenAI parity" becomes "add a provider row and watch its
scenarios go green."

Non-goal: this is not a CI gate and not a wire-format assertion. Wire-format
and provider-internal behaviour are already covered by the WireMock suite
(`connectors-e2e-test-agentic-ai/.../aiagent/wiremock/**`). This test is
deliberately behavioural and provider-agnostic.

## Constraints & context

- **Module:** `connectors-e2e-test/connectors-e2e-test-agentic-ai` (separate
  module; needs `element-templates-cli`, build with `-am`).
- **Native path only.** Must drive the **v2 native template + `configuration.*`
  property namespace** (the surface `NativeAnthropicMessagesWireFormatFixture`
  uses), NOT the v1 `provider.*` ids — those route through the LangChain4j
  bridge and would leave the own-LLM-layer code untested. This is the single
  most important correctness property of the whole test.
- **Local dev only, never CI.** Gated so it self-skips without credentials and
  is never wired into a CI workflow.
- **Seeded from `DocumentToolCallResultsIT`** — reuse its harness shape
  (standalone `@SpringBootTest` + real API + provider matrix + judge), NOT its
  v1 template wiring. Keep `DocumentToolCallResultsIT` untouched (it still
  covers the bridge document path).

## Architecture

New sibling IT (working name **`NativeProviderAcceptanceIT`**) under
`connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/e2e/`.

Harness (mirrors `DocumentToolCallResultsIT`):
- `@SpringBootTest(classes = TestConnectorRuntimeApplication.class)`
- `@CamundaSpringProcessTest` (spins up the engine test container)
- `@WireMockTest` (serves tool payloads: PDF bytes for the document scenario)
- `@Import(CamundaDocumentTestConfiguration.class)`
- Element-template baking via `io.camunda.connector.e2e.{ElementTemplate,BpmnFile,ZeebeTest}`.
- Does **not** extend `BaseAiAgentTest` (that harness is built around a WireMock
  LLM stub; here the LLM is a real remote API).

### Provider matrix

A test-local descriptor collected in a `Stream.of(...).filter(NativeProvider::isEnabled)`
list, mirroring `DocumentToolCallResultsIT.ProviderConfig` but on the native
property namespace:

```
record NativeProvider(
    String label,                    // parameterized-test display name
    String requiredEnvVar,           // e.g. "ANTHROPIC_API_KEY"; isEnabled = flag set AND var present
    Map<String,String> properties,   // configuration.* template overrides
    Set<Capability> capabilities)    // scenario gating
```

`Capability` is a test-local enum: `MULTIMODAL_TOOL_RESULT`, `STRUCTURED_OUTPUT`,
`REASONING`, `PROMPT_CACHING`. (Test-local and explicit — it declares what each
row is expected to support; it does not query the production capability matrix.)

**Rows now** (both support all four capabilities, so both run all scenarios):

| label | model id | backend |
|---|---|---|
| `anthropic-sonnet-4.6` | `claude-sonnet-4-6` | direct |
| `anthropic-sonnet-5` | `claude-sonnet-5` | direct |

Baseline `configuration.*` properties per Anthropic-direct row:
```
configuration.type                          = anthropic
configuration.anthropic.backend.type        = direct
configuration.anthropic.backend.apiKey      = <ANTHROPIC_API_KEY>
configuration.anthropic.model.model         = claude-sonnet-4-6 | claude-sonnet-5
```
(Exact property ids for reasoning-enable/effort and prompt-caching are pinned in
the implementation plan against the current v2 template; see per-scenario setup.)

**Future rows (not built now):** OpenAI Responses, OpenAI Completions,
Anthropic-on-AWS. Adding a row (label + env var + `configuration.*` map +
capability set) is the only change needed; the capability filter automatically
runs only the scenarios that row supports. No local / `openaiCompatible` rows.

### Scenarios

Each scenario is a **separate `@ParameterizedTest`** over the matrix, guarded by
its required capability (`Assumptions.assumeTrue(provider.supports(cap))` or a
capability-filtered method source, pinned in the plan). Both current rows
support everything, so gating is inert today but active the moment a
heterogeneous row (OpenAI) lands.

Every scenario plants **fabricated nonce tokens** (invented strings that cannot
appear from model training, e.g. `Zypherion`, `Kael Thrennix`, `847`) in the
tool output, then asserts those exact tokens surface in the agent's answer —
proving the agent both invoked and consumed the tool. Deterministic assertion
is the hard gate; the LLM judge is a secondary holistic check.

| # | Scenario | Tool mock | Hard (deterministic) gate | Capability |
|---|---|---|---|---|
| 1 | Tool-call loop | `mockJobWorker` returns a record with a nonce fact | answer `contains` the nonce fact | — (always) |
| 2 | Document-in-tool-result | WireMock serves a PDF whose text embeds nonce facts | answer `contains` the doc's nonce facts | `MULTIMODAL_TOOL_RESULT` |
| 3 | Structured output | `mockJobWorker` returns source facts | `agent` output parses against the JSON schema AND carries the planted field values | `STRUCTURED_OUTPUT` |
| 4 | Reasoning enabled | none | run completes AND `metrics.tokenUsage().reasoningTokenCount() > 0` | `REASONING` |
| 5 | Prompt caching | `mockJobWorker` (forces a 2nd model call) | after turn 1 `cacheCreationTokenCount() > 0`; after turn 2 `cacheReadTokenCount() > 0` | `PROMPT_CACHING` |

Per-scenario setup notes:
- **3 (structured output):** set `data.response.format.type=json`,
  `data.response.format.schema=<schema>`, `data.response.format.schemaName=...`
  (the shared response-format surface, not `configuration.*`). Assert via the
  existing parsed-JSON pattern (`hasResponseJsonSatisfying`-style) plus schema
  validation of the `agent` output.
- **4 (reasoning):** enable thinking (+ effort) on the row's `configuration.anthropic.*`
  reasoning props. Hard gate is `reasoningTokenCount() > 0`; the answer must also
  contain a planted fact so we know the run actually produced a usable result.
- **5 (prompt caching):** **capability-gated setup differs per row** — Anthropic
  sets `enablePromptCaching=true`; OpenAI (future) sets nothing (automatic). The
  assertion is uniform. **The system prompt must exceed the model's minimum
  cacheable prefix (~1024 tokens on Sonnet)** — otherwise caching is silently
  skipped and `cache_*` stays 0. Use a long fixed padding system prompt. The
  second model call needed to observe a cache *read* is produced by a tool call
  (turn 1 → tool result → turn 2 re-sends the now-cached prefix).

### Assertions

- **Hard gate (always): deterministic.**
  - Nonce-token `contains` on the final answer text (scenarios 1, 2, 4).
  - JSON schema parse + planted field values (scenario 3).
  - Token-metric thresholds off `response.context().metrics().tokenUsage()`
    (scenarios 4, 5).
- **New assert helpers.** The existing `AgentResponseAssert` /
  `JobWorkerAgentResponseAssert` `.hasMetrics(...)` does **exact whole-record
  equality**, unusable against a real API. Add per-field threshold helpers
  (e.g. `hasReasoningTokensGreaterThanZero()`, `hasCacheReadTokensGreaterThanZero()`,
  `hasCacheCreationTokensGreaterThanZero()`) to the relevant assert class.
- **Backstop (secondary): LLM judge.** `hasVariableSatisfiesJudge("agent", rubric)`
  with a natural-language rubric, where a holistic check adds value. The judge is
  **optional**: it runs only when judge config/credentials are present; when
  absent, scenarios still pass on the deterministic gate alone. It is never the
  sole gate for any scenario.

### Judge configuration

Configured via `@SpringBootTest(properties = { "camunda.process-test.judge.*" })`.
Recommended: **Anthropic-direct Haiku**, reusing `ANTHROPIC_API_KEY` so a local
Anthropic run needs exactly one credential.

**Plan-time verification required:** confirm the `io.camunda.process.test` judge
supports `provider=anthropic` (direct). If it does not (existing tests use
`amazon-bedrock` / `openai`), fall back to Bedrock Haiku (AWS creds) or leave the
judge disabled by default — the deterministic gate stands either way, consistent
with the judge being optional.

### Gating (local-only, never CI)

- Master flag `RUN_NATIVE_LLM_E2E=true` via `@EnabledIfEnvironmentVariable`,
  **plus** per-row required-key presence (`ProviderConfig.isEnabled`).
- Never set in CI → self-skips there. Preferred over `@Disabled`, which would
  require a code edit to run locally.
- Not referenced by any CI workflow file.
- Credentials read via plain `System.getenv` (populated by a local env loader at runtime),
  matching the existing convention.

## Test data & fixtures

- **Nonce facts:** a small fixture of invented strings reused across scenarios,
  chosen so they cannot be produced from training data.
- **Tools:** ad-hoc-subprocess tools whose job workers are stubbed via
  `processTestContext.mockJobWorker(...)` to return the nonce facts (scenarios
  1, 3, 5); the document scenario (2) uses a WireMock-served PDF, reusing the
  `document-tool-call-results.bpmn` pattern (or a dedicated BPMN).
- **Caching padding:** a long constant system prompt (> min cacheable prefix)
  used by scenario 5.

## Out of scope

- Server-tool scenarios (code execution, web search/fetch) — provider-specific,
  covered elsewhere.
- Wire-format / message-structure assertions — WireMock suite owns those.
- Citations.
- CI integration (may come later).

## Open items for the implementation plan

1. Exact v2 template path + constant (`AiAgentTestFixtures` / the native fixture)
   and the exact `configuration.anthropic.*` reasoning-enable, effort, and
   prompt-caching property ids against the current template.
2. Judge `provider=anthropic` support verification (see Judge configuration).
3. Capability-gating mechanism (`assumeTrue` vs filtered method source).
4. Final class name and whether scenario 2 reuses `document-tool-call-results.bpmn`
   or gets a dedicated BPMN.
