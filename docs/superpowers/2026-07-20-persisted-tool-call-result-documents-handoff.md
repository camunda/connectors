# Handoff — Persist the capability-based placement of tool-call-result documents

**Status:** design discussion captured; NOT yet specced or planned. Pick this up in a dedicated session
(brainstorm → spec → plan → implement). Branch context: `agentic-ai/issue-7211-vertical-pilot` (own-LLM-layer
vertical pilot, #7211).

**One-line goal:** the persisted conversation state (agent context + agent-instance history API) must equal
what we actually send to the provider API. Decide *where each tool-call-result document goes* **once, at
ingestion of a new tool-call-result message**, from the resolved model's capabilities, then **persist that
final shape** and **replay it verbatim** on every subsequent request. Stop the current practice of shifting
content transiently at send-time while persisting a different (raw) shape.

---

## Baseline — three states to hold in your head

1. **main (ADR-004, `docs/adr/004-document-handling-in-tool-call-results.md`, PR #6999).** Documents in tool
   results are *always* extracted into a single appended synthetic `UserMessage` (metadata
   `METADATA_TOOL_CALL_DOCUMENTS`): preamble + per-document (`<doc/>` tag, `DocumentContent`) pairs. The
   tool-result text itself **keeps the document as a serialized reference** (standard `DocumentSerializer` →
   `documentId`/`storeId`/metadata), so it is **never empty** and the model correlates the reference with the
   `<doc/>` tag 1:1. The synthetic message **does not count** toward the window and is **evicted together with
   its originating `ToolCallResultMessage`** (never orphaned). Native multi-content tool results were
   **explicitly deferred** there as a "Future optimization" (ADR-004 §"Future optimization (out of scope)").

2. **This branch today (C5, `docs/superpowers/plans/2026-07-09-c5-toolcallresultstrategy.md`).** Made tool
   results *self-describing*: the composer (`AgentConversationTurnInputComposerImpl`) lifts extracted
   documents into `DocumentContent` **inside** each `ToolCallResultContent.content` (deduped by
   `DocumentReference`), and removed the eager synthetic-message extraction. A **send-time, transient**
   `CapabilityAwareToolCallResultStrategy` (package `io.camunda.connector.agenticai.aiagent.multimodal` after
   the 2026-07-20 reshape) runs in `BaseAgentRequestHandler.proceed()` **every loop** on the windowed
   snapshot: per document, keep it inline if `capabilities.toolResultModalities()` supports its
   `DocumentModality`, else **strip** it and emit a **post-window, never-persisted** `<doc/>` synthetic
   `UserMessage`. Persisted state ≠ sent state. For a pure bare-`Document` result the strip leaves `[]` →
   L4J bridge renders `CONTENT_NO_RESULT`.

3. **Target (this handoff).** = ADR-004's deferred "Future optimization" **+ persist the placement**. Keep
   ADR-004's window rules. The capability-matrix decision is the same logic C5 already has; what changes is
   **when** it runs (ingestion, once), **that it is persisted/frozen**, that native-supported blocks now also
   carry the `<doc/>` tag inline, and that the fallback synthetic message is a **real persisted** message
   again.

---

## Target content shapes (persisted == sent == replayed)

Scenario tools: `getReport` returns a bare PDF (**pure** = `content()` is itself a `Document`);
`getWeather` returns `{result:"Sunny", attachment:<PDF>}` (**nested** = document(s) alongside other data).

**Model supports the document's modality in tool results** (matrix `toolResultModalities` ⊇ its `Modality`):
```
result getReport (pure):    [ <doc/> tag, DocumentContent ]
result getWeather (nested): [ ObjectContent(complex), intro text, <doc/> tag, DocumentContent ]
```
- pure → tag + content, **no intro**.
- nested → complex object + **one intro** + then N × (`<doc/>` tag, `DocumentContent`) pairs.

**Model does NOT support it:**
```
result getReport (pure):    [ pointer text, <doc/> tag ]              (+ synthetic user message with bytes)
result getWeather (nested): [ ObjectContent(complex) ]                (+ synthetic user message with bytes)
+ ONE synthetic UserMessage(METADATA_TOOL_CALL_DOCUMENTS):
    [ preamble, <doc/> tag, DocumentContent, <doc/> tag, DocumentContent, ... ]   (one intro/preamble, N pairs)
```
- The synthetic user message is **persisted** (see window rules below), one per turn, aggregating all
  unsupported docs for that turn, exactly like ADR-004.
- **pure-unsupported must NOT be left empty.** Mirror main: keep a **pointer text + the `<doc/>` reference
  tag** in the tool result so it is non-empty *and* 1:1 correlatable with the bytes in the follow-up user
  message (main gets this for free from the serialized reference; our self-describing model must re-add it).
  Nested-unsupported keeps the complex object (which already carries the reference in its JSON) → no pointer
  needed.

Tag shape = existing `DocumentReferenceXmlTag` (`documentId`/`storeId`/`contentType`/`fileName` for Camunda
refs; `url`/`name` for external; `toolName`/`toolCallId` otherwise). Preamble/intro: reuse
`ToolCallResultStrategy.TOOL_CALL_DOCUMENTS_PREAMBLE` (or a close variation) — same wording as the
user-message approach.

---

## Resolved decisions (from the grilling session, 2026-07-20)

- **Q2 — persist + window the fallback synthetic message.** Confirmed: this is exactly ADR-004 behavior. The
  synthetic `METADATA_TOOL_CALL_DOCUMENTS` `UserMessage` is a **persisted** message that **does not count**
  toward `maxMessages` and is **evicted atomically with its originating `ToolCallResultMessage`**. The C5
  "post-window transient, never persisted" approach is dropped; `MessageWindowFilter.isToolCallDocumentMessage`
  special-casing goes **fully live again** for new data.
- **Q3 — extraction shape.** Same structure as the user-message approach: **one intro/preamble, then multiple
  (`<doc/>` tag, document content) pairs**. Reuse the same preamble (or a variation). Pure vs nested defined
  above.
- **Q3 edge — pure-unsupported empty content.** Do NOT leave the tool result empty / `CONTENT_NO_RESULT`.
  Keep a pointer text + the `<doc/>` reference tag in the tool-result content (mirrors main keeping the
  serialized reference). *Sub-question still open:* pointer **+ tag** (recommended, robust when several docs
  across several calls are unsupported) vs pointer-only.
- **Q4 — the capability matrix is the contract.** Confirmed. If a provider/api-family advertises `DOCUMENT`
  (or `IMAGE`) in `toolResultModalities`, it is REQUIRED to faithfully emit ordered `text (+object), <doc/>
  text, document` inside ONE tool-result block; the generic layer decides purely from the matrix and never
  second-guesses the provider. OpenAI Responses feasibility already SDK-verified (Option B: emit native
  `input_file`/`input_image` via `ofResponseFunctionCallOutputItemList`). The matrix-driven decision logic
  already exists in C5; the redesign only relocates/persists it and adds the inline `<doc/>` tag.

---

## OPEN decisions (resolve first in the dedicated session)

- **Q1 — capability freeze vs. model drift on replay.** Placement is frozen from the capabilities of the model
  that **first ingested** the result. If a later turn resolves a *different* model with different
  `toolResultModalities`, we replay the frozen shape as-is. Choose the contract:
  - (a) trust the frozen shape, accept drift, document as "placement follows the ingesting model"; or
  - (b) on replay, re-validate persisted native document blocks against the current model and **fail loud** on
    incompatibility.
  - Lean: (a) as the base behavior, plus fail-loud only when a persisted native block is genuinely
    incompatible with the current model. Decide.
- **The seam — where the one-shot placement runs.** It needs `ChatModelApi.capabilities()`, resolved in
  `BaseAgentRequestHandler.proceed()`; the composer doesn't hold capabilities today. Options:
  - (a) pass capabilities into `AgentConversationTurnInputComposerImpl` so placement happens as the message is
    built; or
  - (b) keep the composer producing the raw self-describing message and add a **one-shot placement step in
    `proceed()`** that transforms **only the newly-added** messages before `storeMessages(...)`.
  - Lean: (b) — keeps the capability dependency in the handler where the model is resolved, and makes "only on
    new tool-call results, not every loop" fall out naturally. Decide.

---

## Likely implementation touchpoints (verify against current code)

- `aiagent/agent/AgentConversationTurnInputComposerImpl.java` — currently lifts docs into TCR content; will
  either receive capabilities (seam a) or stay raw (seam b).
- `aiagent/agent/BaseAgentRequestHandler.java` — currently applies the strategy transiently every loop; move
  to a one-shot placement on new results before persist; keep windowing per loop over already-placed messages.
- `aiagent/multimodal/CapabilityAwareToolCallResultStrategy.java` + `ToolCallResultStrategy.java` — convert
  from a send-only transform returning a snapshot into an **ingestion-time placement** that produces the
  persisted messages (TCR content + persisted synthetic `UserMessage`); add the inline `<doc/>` tag to the
  native-supported branch; add the pure-unsupported pointer+tag.
- `aiagent/memory/**` `MessageWindowFilter` — `isToolCallDocumentMessage` becomes live again (not-counted +
  evict-with-TCR). Confirm atomic eviction still holds with the persisted synthetic message.
- `aiagent/agentinstance/AgentInstanceHistoryMapper.java` — now naturally persists the final shape (TCR text
  blocks incl. `<doc/>`, `DocumentContent` for Camunda refs → `AgentInstanceHistoryContent.document(...)`; the
  synthetic message → a USER history item). Verify the history API renders it sensibly.
- Native emitters — `chatmodel/provider/anthropic/AnthropicMessageRequestConverter.java`,
  `chatmodel/provider/openai/family/responses/OpenAiResponsesRequestConverter.java`,
  `chatmodel/provider/openai/family/completions/OpenAiCompletionsRequestConverter.java`: render an ordered
  `[text, <doc/> text, document]` inside one tool-result block when the matrix advertises the modality (OpenAI
  Responses = Option B native `input_file`/`input_image`; Completions = text-only → always fallback).
- `aiagent/capabilities/**` `ModelCapabilities.toolResultModalities()` + the capability matrix per
  provider/api-family — the single source of truth for the placement decision.
- `DocumentReferenceXmlTag`, `ToolCallResultStrategy.TOOL_CALL_DOCUMENTS_PREAMBLE` — reused for the inline tags
  and preamble.
- `ToolCallResultDocumentExtractor` / `ContentTreeDocumentWalker` — unchanged gateway-aware extraction; still
  detects pure (bare `Document`) vs nested.

## Test / BC notes

- Docs-in-tool-results is 8.10-only and never shipped in a stable release ⇒ **no stored-data BC obligation**
  for this shape (per C5). 8.9-persisted golden fixtures (C4) must stay green.
- e2e: the L4J bridge (`toolResult:[TEXT]`) always takes the fallback → assert byte-identical to ADR-004's
  user-message rendering (`ToolCallResultDocumentAssertions`). Native providers get inline-block e2e coverage
  via the streaming WireMock job-worker tests (now under `aiagent.jobworker.<provider>`).
- Add multi-turn window tests: document renders while its turn is in-window; gone (atomically) once evicted;
  effective window count unchanged by carried documents.

## References

- ADR: `connectors/agentic-ai/docs/adr/004-document-handling-in-tool-call-results.md`
- C5 plan: `docs/superpowers/plans/2026-07-09-c5-toolcallresultstrategy.md`
- Vertical-pilot design: `docs/superpowers/specs/2026-07-08-vertical-pilot-own-llm-layer-design.md` §6–§8
- Landing follow-ups (item 7 = conversation variable bloat, adjacent): `docs/superpowers/own-llm-layer-landing-followups.md`
- OpenAI Option-B tool-result-document feasibility spike (native `input_file`/`input_image` in
  `function_call_output`) — recorded in the native OpenAI provider work.
