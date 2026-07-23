# Task 0: Native Anthropic Branch Reconnaissance — Findings

**Branch:** `agentic-ai/native-anthropic-provider` (stacked on `agentic-ai/v2-config-types`)
**Worktree:** `/Users/mathias.geat/orca/workspaces/connectors/agentic-ai-native-anthropic-provider`
**Date:** 2026-07-23

Status: **no contradictions found** — all four checks confirm the plan's assumptions. No STOP/ESCALATE
condition triggered.

---

## 1. SPI shape: does the chat-model interface declare `capabilities()`?

**Finding: NO.** The interface is called `ChatModel` (not `ChatModelApi` — see naming note below) and
declares exactly two members:

`connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/chatmodel/ChatModel.java:14-19`

```java
public interface ChatModel extends AutoCloseable {

  ChatResult execute(ChatRequest request);

  @Override
  void close();
}
```

No `capabilities()` method, and no reference to a `ModelCapabilities` type anywhere on this branch:

```
grep -rln "ModelCapabilities" --include="*.java" .   # zero matches in the whole repo
```

The neighboring SPI types confirm the same shape — no capability surface anywhere in the chat-model
package:

- `ChatModelConfiguration.java:13-20` — just `provider()` / `model()`.
- `ChatModelFactory.java` — `supports(ChatModelConfiguration)` / `create(ChatModelConfiguration)`.
- `ChatModelRegistry.java` — `resolve(ChatModelConfiguration)`.

**Naming note (matters for later tasks):** the brief and plan refer to `chatmodel/ChatModelApi.java`.
That name belongs to the *pilot/reference* branch (`agentic-ai/issue-7211-vertical-pilot`) — its
commits (e.g. `9903be0eff feat(agentic-ai): introduce the ChatModelApi provider SPI...`,
`0bed5c10f6 feat(agentic-ai): add ModelCapabilities to ChatModelApi...`) are present in this repo's
git object store (reachable via `git log --all`) but are **not** on this branch's ancestry. On
*this* branch/worktree the type was introduced directly as `ChatModel` in a single commit:

```
7f322aff80 feat(agentic-ai): introduce the ChatModel provider SPI with continuation support
```

— i.e. it was never named `ChatModelApi` here; there was no local rename to reconcile. Notably, the
pilot's `0bed5c10f6` commit *did* add `ModelCapabilities` to the pilot's `ChatModelApi` — that
capability surface was evidently dropped/deferred when this branch's `ChatModel` was authored fresh.
Later tasks should reference `ChatModel` (this package), not `ChatModelApi`.

---

## 2. `<doc/>` extraction: class + method, and capability-gating check

**Finding:** the ADR-004 behavior (strip `Document`s out of tool-call-result content, re-insert as a
synthetic `UserMessage`) lives in:

- **Extraction:** `ToolCallResultDocumentExtractor.extractDocuments(List<ToolCallResult>)`
  `connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/agent/ToolCallResultDocumentExtractor.java:51-70`
  (delegates per-result to `extractFromToolCallResult`, lines 72-90, which prefers a
  `GatewayToolHandler.extractDocuments` override and otherwise falls back to
  `ContentTreeDocumentWalker.extractDocumentsFromContent`).

- **Re-insertion as synthetic `UserMessage`:**
  `AgentConversationTurnInputComposerImpl.createDocumentMessageForToolResults(List<ToolCallResult>)`
  `connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/agent/AgentConversationTurnInputComposerImpl.java:204-218`,
  called unconditionally from `compose(...)` at line 120 whenever the turn is expecting tool-call
  results (guarded only by `expectingToolCallResults`/`orderedToolCallResults` — a
  conversation-shape check, not a capability check). The same extractor + synthetic-`UserMessage`
  pattern is reused for event-result documents in `createEventMessage` (lines 220-251, extraction at
  line 241).

- Documents are rendered as `<doc/>` XML tag + content pairs via
  `DocumentReferenceXmlTag.from(...).toXml()` in `createDocumentPairs` (lines 253-266); preambles are
  the `TOOL_CALL_DOCUMENTS_PREAMBLE` / `EVENT_DOCUMENTS_PREAMBLE` constants (lines 58-61).

**Confirmed NOT gated by any `ModelCapabilities`:** neither `ToolCallResultDocumentExtractor.java` nor
`AgentConversationTurnInputComposerImpl.java` imports or references any capability/`ModelCapabilities`
type (confirmed above — no such type exists on this branch at all). The behavior is unconditional for
every provider that reaches `compose(...)`.

---

## 3. v1 Anthropic parameter surface

Source: `AnthropicProviderConfiguration.java`
(`connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/model/request/v1/AnthropicProviderConfiguration.java`),
cross-checked against the generated v1 element template
(`connectors/agentic-ai/connector-agentic-ai/element-templates/agenticai-aiagent-job-worker.json`,
`provider.anthropic.*` properties) and the factory that consumes it
(`AnthropicChatModelFactory.java`).

Discriminator: `@TemplateSubType(id = "anthropic", label = "Anthropic")` (line 22); `provider()`
returns the fixed constant `ANTHROPIC_ID = "anthropic"` (lines 27, 29-32).

Full parameter list (record path → element-template property id):

| Field | Type | Required? | Java record path | Template property id |
|---|---|---|---|---|
| Endpoint | String (URL) | optional | `anthropic.endpoint` | `provider.anthropic.endpoint` |
| API key | String | required | `anthropic.authentication.apiKey` | `provider.anthropic.authentication.apiKey` |
| Timeout | ISO-8601 `Duration` string | optional | `anthropic.timeouts.timeout` | `provider.anthropic.timeouts.timeout` |
| Model | String | required | `anthropic.model.model` | `provider.anthropic.model.model` |
| Maximum tokens | Integer (`@Min(0)`) | optional | `anthropic.model.parameters.maxTokens` | `provider.anthropic.model.parameters.maxTokens` |
| Temperature | Double (`@Min(0)`) | optional | `anthropic.model.parameters.temperature` | `provider.anthropic.model.parameters.temperature` |
| top P | Double (`@Min(0)`) | optional | `anthropic.model.parameters.topP` | `provider.anthropic.model.parameters.topP` |
| top K | Integer (`@Min(0)`) | optional | `anthropic.model.parameters.topK` | `provider.anthropic.model.parameters.topK` |

File:line references (`AnthropicProviderConfiguration.java`):
- `endpoint` — lines 40-47
- `authentication.apiKey` — lines 52-66 (52-60 field, `toString()` redacts it at 62-65)
- `timeouts.timeout` — line 49 (type defined in `v1/shared/TimeoutConfiguration.java:13-23`)
- `model.model` — lines 68-81
- `model.parameters.maxTokens` — lines 84-94
- `model.parameters.temperature` — lines 95-104
- `model.parameters.topP` — lines 105-114
- `model.parameters.topK` — lines 115-124

**Notably absent:** there is **no `stopSequences`** (or any stop-sequence) parameter in the v1 Anthropic
config, confirmed both in the Java record and in `AnthropicChatModelFactory.java` (which only reads
`maxTokens`/`temperature`/`topP`/`topK` — no stop-sequence wiring at all). This is the acceptance
checklist for Task 4: **endpoint, apiKey, timeout, model, maxTokens, temperature, topP, topK** — 8
parameters total, no more, no less.

---

## 4. v2 `ProviderConfiguration` union — current shape + rename target

File: `connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/model/request/v2/ProviderConfiguration.java`

- **Bare type name:** `ProviderConfiguration` (not `V2ProviderConfiguration` — that name belongs only
  to the pilot/reference branch; later tasks must widen *this* real union, in *this* package).
- **Package:** `io.camunda.connector.agenticai.aiagent.model.request.v2`
- **Kind:** `public sealed interface ProviderConfiguration extends ChatModelConfiguration` (line 33),
  `permits CustomProviderConfiguration` (line 34) — currently a **single-member** sealed union.
- **Discriminator property:** `"type"` — via `@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")`
  (line 25) and mirrored in the element-template discriminator
  `@TemplateDiscriminatorProperty(name = "type", group = "provider", defaultValue = CUSTOM_ID)`
  (lines 27-32).
- **`@JsonSubTypes`:** `@JsonSubTypes({@JsonSubTypes.Type(value = CustomProviderConfiguration.class, name = CUSTOM_ID)})`
  (line 26) — `CUSTOM_ID = "custom"` (defined in `CustomProviderConfiguration.java:55`).
- **Interface contract:** `provider()` (line 38), `model()` (line 42, both `@Override` of
  `ChatModelConfiguration`), plus a v2-only `default @Nullable String backend()` (lines 45-47,
  defaults to `null` per the branch tip commit `486d0ddae9`) — "the backend discriminator (e.g.
  `direct`, `bedrock`, `compatible`)".
- **`CustomProviderConfiguration` member** (`CustomProviderConfiguration.java`):
  - `@TemplateSubType(id = "custom", label = "Custom Implementation (Self-Managed/Hybrid only)")` (line 23)
  - `record CustomProviderConfiguration(String providerType, String model, Map<String,Object> parameters)`
    (lines 24-51), all three fields `@FEEL`-enabled (`FeelMode.optional`/`FeelMode.required`).
  - `provider()` returns the **runtime-supplied** `providerType` field (lines 57-60) — *not* the fixed
    `CUSTOM_ID` constant. This differs from the v1 pattern (`AnthropicProviderConfiguration.provider()`
    returns the fixed `ANTHROPIC_ID` constant) and from what a new native `AnthropicProviderConfiguration`
    v2 member should do: it should return a fixed provider id, following the v1/ADR precedent, not a
    user-supplied string.
  - No `backend()` override — inherits the `null` default from the interface.

**Rename-scheme implication for later tasks:** the real union to widen is
`io.camunda.connector.agenticai.aiagent.model.request.v2.ProviderConfiguration` (bare name
`ProviderConfiguration`), *not* `V2ProviderConfiguration`. A new `AnthropicProviderConfiguration`
member in this v2 package must:
- be added to the `permits` clause (line 34) and to `@JsonSubTypes` (line 26),
- declare its own `@TemplateSubType(id = "anthropic", ...)`,
- fix `provider()` to a constant `"anthropic"` id (mirroring `CustomProviderConfiguration`'s constant
  pattern, not its dynamic `provider()` override), and
- decide what it returns from `backend()` (`direct` / `bedrock` / `compatible` per the interface
  doc-comment) rather than relying on the inherited `null` default.

There is currently no `v2` package sibling named `AnthropicProviderConfiguration` — the only
`AnthropicProviderConfiguration` on this branch is the v1 one investigated in §3.

---

## Summary table

| # | Question | Answer |
|---|---|---|
| 1 | Does the chat-model SPI declare `capabilities()`? | **No.** `ChatModel` (chatmodel/ChatModel.java) has only `execute()`/`close()`. No `ModelCapabilities` type exists anywhere on this branch. |
| 2 | `<doc/>` extraction class/method, capability-gated? | `ToolCallResultDocumentExtractor.extractDocuments` + `AgentConversationTurnInputComposerImpl.createDocumentMessageForToolResults`; unconditional, not gated by any capability. |
| 3 | v1 Anthropic parameter list | endpoint, apiKey, timeout, model, maxTokens, temperature, topP, topK (8 total; no stopSequences). |
| 4 | v2 union shape | `ProviderConfiguration` (sealed interface, pkg `...model.request.v2`), discriminator `"type"`, `permits CustomProviderConfiguration` only, `@JsonSubTypes` maps `"custom"` → `CustomProviderConfiguration`. |
