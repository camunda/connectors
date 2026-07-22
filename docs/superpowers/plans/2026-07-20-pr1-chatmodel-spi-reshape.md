# PR1 — Generic ChatModelApi SPI + message-model reshape (v1/Langchain4J) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the generic own-LLM-layer abstraction (`ChatModelApi` SPI, content-block message model, continuation loop, cache/reasoning metrics) onto `main`, backed only by the existing Langchain4J providers, with **no observable behavior change**.

**Architecture:** This is a **port + reshape of an existing reference implementation**, not greenfield. The pilot branch `agentic-ai/issue-7211-vertical-pilot` (HEAD `48433a0d63`) is the reference for the target end state; PR1 reproduces only its *generic* layer (v1/L4J) on top of `origin/main` (`f4a361cf43`). Five coherent, individually-compiling commits: (1) package relocation, (2) new content blocks, (3) tool-call-result shape, (4) the atomic `ChatModelApi` SPI reshape, (5) cache/reasoning metrics. The existing test suite is the behavior-identity gate — no e2e test changes.

**Tech Stack:** Java 21 (sealed types, record patterns, exhaustive switch), Jackson polymorphic (de)serialization, LangChain4J 1.x, Spring Boot autoconfiguration, JUnit 5 + Mockito + AssertJ, `io.soabase.recordbuilder`, Maven multi-module reactor.

## Global Constraints

- **Base branch:** cut a NEW branch off `origin/main` (`f4a361cf43`). This is the PR1 branch; the pilot branch is reference-only and never merged.
- **Behavior identity is the bar:** the full existing e2e + unit suite must stay green. Unit tests may be *retyped* for internal DTO shape changes (mechanical, zero assertion changes); **e2e tests and their assertions must not change**. If an e2e assertion needs changing, out-of-scope logic leaked in.
- **The pilot has the PR3 rename baked in.** Everywhere the pilot shows `V1ProviderConfiguration` / `io.camunda.connector.agenticai.aiagent.model.request.v1.*`, PR1 uses the **main** names: `ProviderConfiguration` / `io.camunda.connector.agenticai.aiagent.model.request.provider.*`. Translate mechanically. Likewise the pilot shows packages under `aiagent.chatmodel.*` from commit 1 onward — correct for PR1 after commit 1 lands.
- **Do NOT port anything from these excluded pilot areas:** `capabilities/**` (PR2), `model/request/v2/**` + `AiAgent*V2Function` (PR3), `chatmodel/provider/anthropic/**` (PR4), `chatmodel/provider/openai/**` (PR5), `multimodal/**` + `CapabilityAwareToolCallResultStrategy` (deferred follow-up).
- **`transport` package is deferred.** `HttpTransportSupport` + `okHttpProxy()` land with the first native provider (PR4/PR5), not here.
- **Commit messages describe the change only.** Never mention the pilot, `C*` steps, review, crit, rounds, tasks, or agents.
- **Never push without explicit approval.** Never touch `connectors-e2e-test/.../native-provider-acceptance-agent.bpmn`.
- **Build/test runs use `dangerouslyDisableSandbox: true`** (Mockito MockMaker + git break in the sandbox).
- **Preferred move mechanism:** JetBrains MCP move / `rename_refactoring` (auto-updates ~70 referencing files; keeps git rename-clean). Requires the PR1 worktree open + indexed in IntelliJ. Fallback: `git mv` + compiler-driven import fixes.

### Module paths & verify commands

- Main module: `connectors/agentic-ai/connector-agentic-ai`
- e2e module (separate; must at least test-compile after any model/serialization change): `connectors-e2e-test/connectors-e2e-test-agentic-ai`
- Package root: `io.camunda.connector.agenticai.aiagent` → dir `connectors/agentic-ai/connector-agentic-ai/src/{main,test}/java/io/camunda/connector/agenticai/aiagent`

Full module build + tests (per commit exit gate):
```bash
mvn -q -pl connectors/agentic-ai/connector-agentic-ai -am test -DdangerouslyDisableSandbox=ignored
# (run with dangerouslyDisableSandbox: true on the Bash tool; the -D above is a no-op placeholder — do not rely on it)
```
Actual command to run (Bash tool `dangerouslyDisableSandbox: true`):
```bash
mvn -q -pl connectors/agentic-ai/connector-agentic-ai -am test
```
Targeted test run:
```bash
mvn -q -pl connectors/agentic-ai/connector-agentic-ai test -Dtest='ClassA,ClassB'
```
e2e module test-compile (after commits 2/3/4/5, i.e. any model change):
```bash
mvn -q clean test-compile -f connectors-e2e-test/pom.xml -pl connectors-e2e-test-agentic-ai -am
```
Read pass/fail counts from `target/surefire-reports/*.xml`, not mvn stdout.

### Reference-diff mechanics (how to port)

To see the pilot's version of any file (translate names per the constraints above):
```bash
git show 48433a0d63:<pilot-path>              # pilot end state
git show origin/main:<main-path>              # main baseline
git diff origin/main 48433a0d63 -- <path>     # the pilot delta (informational; PR1 is a fresh reshape, not a cherry-pick)
```

---

## Task 1 — Package relocation (`framework` → `chatmodel`)

**Deliverable:** the `aiagent.framework.*` tree is relocated to `aiagent.chatmodel.*` / `aiagent.chatmodel.provider.langchain4j.*`, all types keeping their current names and bodies, all references updated, `aiagent.framework` package gone. Pure moves only — zero behavior change.

**Files — moves (old name kept; `framework` → `chatmodel`):**
- `framework/AiFrameworkAdapter.java` → `chatmodel/AiFrameworkAdapter.java`
- `framework/AiFrameworkChatResponse.java` → `chatmodel/AiFrameworkChatResponse.java`
- `framework/package-info.java` → `chatmodel/package-info.java`
- `framework/langchain4j/**` → `chatmodel/provider/langchain4j/**` — every file, unchanged content:
  `ChatMessageConverter(+Impl)`, `ChatModelHttpProxySupport`, `CloseableChatModel(+Delegate)`,
  `ContentConverter(+Impl)`, `ChatModelFactory(+Impl)`, `Langchain4JAiFrameworkAdapter`,
  `Langchain4JAiFrameworkChatResponse`, `package-info.java`, `configuration/*` (both `@Configuration` classes +
  package-info), `document/*`, `jsonschema/*`, `tool/*`, `provider/*` (the 6 `*ChatModelProvider`,
  `ChatModelProviderRegistry`, `ChatModelProviderSupport`, `AwsBedrockRuntimeAuthenticationCustomizer`,
  package-info).
- Mirror all co-located `src/test` files to the same new packages (names unchanged).

**Files — NOT moved / deferred:**
- **No `transport` package.** `ChatModelHttpProxySupport` is a plain move; do **not** extract `HttpTransportSupport` or add `okHttpProxy()` (PR4/PR5).

**CRITICAL — move with pre-reshape bodies.** Four files have later-commit content bundled into the pilot's version. In Task 1 move them **exactly as they are on `origin/main`** (only the `package`/import lines change). Their reshape happens in later tasks:
- `chatmodel/provider/langchain4j/ChatMessageConverterImpl.java` — `StopReason` mapping → Task 4.
- `chatmodel/provider/langchain4j/ContentConverterImpl.java` — `ReasoningContent`/`ProviderContent` arms → Task 2.
- `chatmodel/provider/langchain4j/tool/ToolCallConverter.java` + `ToolCallConverterImpl.java` — `List<Content>` signature → Task 3.
Also: `provider/ChatModelProviderSupport.java` has a 26-line pilot delta — that's Task 4/5 factory-support material; move the **main** body now.

**Behavior-identity constraints:** package/import lines only. No type renames, no body changes, no bean-name changes (the two `@Configuration` classes keep `ChatModelProvider<T>`/`ChatModelFactory` bean methods and the `@ConditionalOnProperty("camunda.connector.agenticai.framework")` gate verbatim).

**Gating tests:** every moved `*Test`, plus the highest-value integration gate `autoconfigure.AgenticAiConnectorsAutoConfigurationTest` (boots the full L4J bean graph). Zero test-content changes — only package paths.

**Reference-update surface:** ~70 files reference `io.camunda.connector.agenticai.aiagent.framework` on main; zero outside `connectors/agentic-ai`. Use the IDE move so references auto-update. Plus one doc line: `connectors/agentic-ai/docs/reference/ai-agent.md` (~line 1616) hardcodes the old FQN in a layering rule — update it in this commit.

**Steps:**
- [ ] **1.1** Confirm the PR1 worktree is open + indexed in IntelliJ (for MCP move). If not available, use `git mv` + compiler-driven fixes.
- [ ] **1.2** Create target packages: `chatmodel`, `chatmodel/provider`, `chatmodel/provider/langchain4j` (+ its `configuration`,`document`,`jsonschema`,`tool`,`provider` sub-packages). Check the sibling-package convention: `capabilities`/`multimodal` have `package-info.java`; add one to `chatmodel.provider` only if the convention requires it (the pilot has none there).
- [ ] **1.3** Move `framework/langchain4j/**` → `chatmodel/provider/langchain4j/**` via IDE move refactoring (references auto-update). For the four bundled files, verify after the move that the body is byte-identical to `origin/main` (only package/imports differ) — `git diff origin/main -- <target>` should show only package/import lines.
- [ ] **1.4** Move `framework/AiFrameworkAdapter.java`, `framework/AiFrameworkChatResponse.java`, `framework/package-info.java` → `chatmodel/`. Delete the now-empty `framework` package.
- [ ] **1.5** Move all mirrored `src/test` files to the new packages.
- [ ] **1.6** Update `connectors/agentic-ai/docs/reference/ai-agent.md` ~line 1616: `...aiagent.framework.langchain4j.**` → `...aiagent.chatmodel.provider.langchain4j.**`.
- [ ] **1.7** Verify staged diff is rename-clean: `git add -A && git diff --cached --stat -M` should show renames (`R`), not delete+add pairs. Confirm no content deltas beyond package/imports: `git diff --cached -M` spot-check.
- [ ] **1.8** Build + test: `mvn -q -pl connectors/agentic-ai/connector-agentic-ai -am test` → green. Then e2e test-compile.
- [ ] **1.9** Commit: `refactor(agentic-ai): relocate the LLM framework package to aiagent.chatmodel`

---

## Task 2 — New content blocks (`ReasoningContent`, `ProviderContent`)

**Deliverable:** two new `Content` subtypes exist, register in the sealed hierarchy + Jackson polymorphic infra, and the two exhaustive `Content` switches gain compiling arms. No v1 code path produces either type — pure additions.

**Files — create:**
- `model/message/content/ReasoningContent.java`
- `model/message/content/ProviderContent.java`
- `model/message/content/ReasoningContentTest.java`
- `model/message/content/ProviderContentTest.java`

**Files — modify:**
- `model/message/content/Content.java` — add sealed permits + `@JsonSubTypes`.
- `agentinstance/AgentInstanceHistoryMapper.java` — two arms in `toHistoryContent(Content)`.
- `agentinstance/AgentInstanceHistoryMapperTest.java` — cover the two arms.
- `chatmodel/provider/langchain4j/ContentConverterImpl.java` — `ReasoningContent` arm (and `ProviderContent` arm iff the switch is exhaustive — see below).

**Target shapes (land the FINAL two-field `ReasoningContent` — no `text` field):**
```java
// ReasoningContent.java
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReasoningContent(
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Object providerPayload,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) @Nullable Map<String, Object> metadata)
    implements Content {}
```
```java
// ProviderContent.java
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProviderContent(
    String provider,
    String blockType,
    Object payload,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) @Nullable Map<String, Object> metadata)
    implements Content {
  public static ProviderContent providerContent(String provider, String blockType, Object payload) {
    return new ProviderContent(provider, blockType, payload, null);
  }
}
```
```java
// Content.java — add the two subtype entries + permits
@JsonSubTypes({
  @JsonSubTypes.Type(value = TextContent.class, name = "text"),
  @JsonSubTypes.Type(value = DocumentContent.class, name = "document"),
  @JsonSubTypes.Type(value = ObjectContent.class, name = "object"),
  @JsonSubTypes.Type(value = ReasoningContent.class, name = "reasoning"),
  @JsonSubTypes.Type(value = ProviderContent.class, name = "provider")
})
public sealed interface Content
    permits TextContent, DocumentContent, ObjectContent, ReasoningContent, ProviderContent {
  Map<String, Object> metadata();
}
```
```java
// AgentInstanceHistoryMapper.toHistoryContent(Content) — add two arms
case ReasoningContent reasoningContent -> objectHistoryContent(reasoningContent);
case ProviderContent providerContent -> objectHistoryContent(providerContent.payload());
```
```java
// ContentConverterImpl — add reasoning arm (dormant; L4J has no reasoning representation)
case ReasoningContent reasoningContent ->
    throw new UnsupportedOperationException(
        "Reasoning content is not supported by the LangChain4J framework abstraction");
```

**Verify at implementation time:**
- `Content.java`'s `@JsonTypeInfo` discriminator is `property = "type"`, `use = Id.NAME` (so `ProviderContentTest` can assert `"type":"provider"`).
- Whether `ContentConverterImpl`'s switch is a Java *exhaustive* switch over `Content`. If yes, add a `ProviderContent` arm too (mirror the `UnsupportedOperationException` throw). If it has a `default`, only the `ReasoningContent` arm is needed for correctness but adding both is harmless and clearer.
- Grep `case TextContent`/`case ObjectContent`/`case DocumentContent` to confirm these two (`AgentInstanceHistoryMapper`, `ContentConverterImpl`) are the only production switches over `Content` on main. Any `AwsAgentCore` content switch does **not** exist yet on main (it's introduced by Task 3) — nothing to touch here.

**Behavior-identity constraints:** existing `Content` subtypes' JSON is byte-unchanged; `@JsonTypeInfo` untouched (only new `@JsonSubTypes.Type` entries). No v1 path produces `ReasoningContent`/`ProviderContent`, so a full-suite run behaves identically.

**Gating / new tests:** `ReasoningContentTest` (JSON round-trip of opaque payload), `ProviderContentTest` (direct + via-`Content` round-trip asserting `"type":"provider"`), `AgentInstanceHistoryMapperTest` (both arms → `AgentInstanceHistoryContent.object(...)`).

**Steps:**
- [ ] **2.1** Create `ReasoningContent`/`ProviderContent` with the shapes above + their unit tests. Run the two new tests → green.
- [ ] **2.2** Edit `Content.java` (permits + `@JsonSubTypes`). Confirm `@JsonTypeInfo` discriminator.
- [ ] **2.3** Add the two arms to `AgentInstanceHistoryMapper.toHistoryContent`; add test cases. Run `AgentInstanceHistoryMapperTest` → green.
- [ ] **2.4** Add the `ReasoningContent` (and, if exhaustive, `ProviderContent`) arm to `ContentConverterImpl`.
- [ ] **2.5** Build + test the module → green; e2e test-compile.
- [ ] **2.6** Commit: `feat(agentic-ai): add reasoning and provider content blocks`

---

## Task 3 — Tool-call-result shape (`Object` → `List<Content>`)

**Deliverable:** `ToolCallResultMessage.results` is `List<ToolCallResultContent>` (a new persisted type whose `content` is `List<Content>`), reads back 8.9-persisted data, and renders byte-identically to the model and history. **ADR-004 synthetic `<doc/>` message preserved.**

**Files — create:**
- `model/tool/ToolCallResultContent.java` (new persisted type + nested Jackson proxy builder + `ContentJsonDeserializer`).
- `model/tool/ToolCallResultContentTest.java`.
- `model/message/ToolCallResultMessageBackwardCompatibilityTest.java` + 8.9 golden JSON fixtures under `src/test/resources/backwardcompatibility/` (see gating tests).

**Files — modify:**
- `model/message/ToolCallResultMessage.java` — `List<ToolCallResult>` → `List<ToolCallResultContent>`.
- `agent/AgentConversationTurnInputComposerImpl.java` — split result-resolution from message-building; lift via `ToolCallResultContent::from`; **leave `createDocumentMessageForToolResults(List<ToolCallResult>)` and `TOOL_CALL_DOCUMENTS_PREAMBLE` untouched, in this class.**
- `agentinstance/AgentInstanceHistoryMapper.java` — `toolResultHistoryItem(ToolCallResultContent...)`; replace bespoke `toolResultContent(Object)` with the generic `contentBlocks(List<Content>)`.
- `chatmodel/provider/langchain4j/tool/ToolCallConverter.java` + `ToolCallConverterImpl.java` — signature `ToolCallResult` → `ToolCallResultContent`; body iterates `List<Content>`.
- `memory/conversation/awsagentcore/mapping/AwsAgentCoreConversationMapper.java` + `BlobEnvelope.java` — retype to `List<ToolCallResultContent>`; add `contentElementToSummaryString(Content)`.
- Retype-only test updates (mechanical, zero assertion changes): `AgentInstanceHistoryMapperTest`, `CamundaAgentInstanceClientTest`, `ChatMessageConverterTest`, `tool/ToolCallConverterTest`, `AwsAgentCoreConversationStoreTest`, `AwsAgentCoreConversationMapperTest`, `BlobEnvelopeTest`, `AgentConversationTurnInputComposerImplTest`, and helper `TestMessagesFixture` (add internal `.map(ToolCallResultContent::from)`).

**Files — must NOT change:** `model/tool/ToolCallResult.java` (transient DTO, unchanged); `memory/runtime/MessageWindowFilter.java` and `model/TurnReconstructor.java` (verified NOT touchpoints); everything under `connectors-e2e-test-agentic-ai` — **especially `aiagent/ToolCallResultDocumentAssertions.java` which keeps its hardcoded preamble literal.** If that e2e helper needs to change, out-of-scope logic leaked in.

**Target shapes:**
```java
// ToolCallResultMessage.java
public record ToolCallResultMessage(
    List<ToolCallResultContent> results,   // was List<ToolCallResult>
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> metadata)
    implements ToolCallResultMessageBuilder.With, Message { ... }
```
```java
// model/tool/ToolCallResultContent.java (abbreviated — see pilot 48433a0d63:.../model/tool/ToolCallResultContent.java)
public record ToolCallResultContent(
    @Nullable String id, @Nullable String name,
    List<Content> content,                 // was Object content
    @Nullable String elementId, @Nullable OffsetDateTime completedAt,
    Map<String, Object> properties)
    implements ToolCallResultContentBuilder.With {

  public ToolCallResultContent {
    content = content == null ? List.of() : List.copyOf(content);
    properties = properties == null ? Map.of() : Collections.unmodifiableMap(new HashMap<>(properties));
  }
  public static ToolCallResultContentBuilder builder() { return new ToolCallResultContentJacksonProxyBuilder(); }
  public static ToolCallResultContent from(ToolCallResult result) { /* id/name/elementId/completedAt/properties + contentFromObject */ }

  static List<Content> contentFromObject(@Nullable Object content) {
    return switch (content) {
      case null -> List.of();
      case String s -> s.isBlank() ? List.of() : List.of(TextContent.textContent(s));
      case Document document -> List.of(DocumentContent.documentContent(document));
      default -> List.of(ObjectContent.objectContent(content));
    };
  }
  // + ToolCallResultContentJacksonProxyBuilder (null-tolerant properties, custom content(...) setter → ContentJsonDeserializer)
  // + ContentJsonDeserializer implements JsonDeserializer<List<Content>>  (see BC note)
}
```
`builder()` returns the Jackson proxy builder even for non-Jackson callers (the generated builder's `Map.copyOf` NPEs on null-valued properties before the compact ctor runs — needed for 8.9 BC data).

**8.9 backward-compat (`ContentJsonDeserializer`):** on read, accept **both** shapes:
- New: `content` is a JSON array whose every element has a `"type"` discriminator ∈ `Content`'s subtype names → normal `List<Content>`.
- Legacy (8.9): anything else (bare string / object / document-ref / scalar / untyped array) → lift via the same three-way branch as `contentFromObject`.
The discriminator set must be **derived from / kept in sync with `Content`'s `@JsonSubTypes` names** and **include `"provider"`** (Task 2 landed `ProviderContent` in the same PR — do not copy a stale `Set.of("text","document","object","reasoning")` snapshot).

**On WRITE we always emit the new shape.** The BC handling is read-only: a custom deserializer accepts legacy 8.9 JSON, but serialization is the standard polymorphic writer, which always produces the new `content: [ {type: …}, … ]` array. Legacy flat shape is never written again — it is only ever *encountered* when reading a pre-existing 8.9-persisted `agentContext`.

**Composer split (the ADR-004-preserving change):**
```java
// resolveOrderedToolCallResults(...) returns Optional<List<ToolCallResult>> (matching/interrupt logic unchanged)
final var results = orderedToolCallResults.get();
final var toolCallResultMessage =
    ToolCallResultMessage.builder()
        .results(results.stream().map(ToolCallResultContent::from).toList())   // the lift
        .metadata(defaultMessageMetadata())
        .build();
messages.add(toolCallResultMessage);
var documentMessage = createDocumentMessageForToolResults(results);  // UNCHANGED: still List<ToolCallResult>
if (documentMessage != null) { messages.add(documentMessage); }
```

**L4J `ToolCallConverterImpl` body:**
```java
private String contentAsString(String toolName, List<Content> content) {
  return content.stream().map(e -> contentElementAsString(toolName, e)).collect(Collectors.joining("\n"));
}
private String contentElementAsString(String toolName, Content content) {
  return switch (content) {
    case TextContent t -> t.text();
    case ObjectContent o -> objectMapper.writeValueAsString(o.content());
    case DocumentContent d -> objectMapper.writeValueAsString(d.document());
    case ReasoningContent r -> throw new IllegalArgumentException("Reasoning content is not expected in tool call results");
    case ProviderContent p -> throw new IllegalArgumentException("Provider content is not expected in tool call results");
  };
}
```
Because `contentFromObject` produces a 0- or 1-element list in PR1, the join degenerates to the old single-value stringification for every real case.

**⚠️ Resolve the `""` vs `null` empty-content delta:** main's `contentAsString(null)` returned `null`; the new empty-list join returns `""`. Before committing, either (a) confirm `dev.langchain4j.data.message.ToolExecutionResultMessage` + downstream prompt/wire rendering treat `null` and `""` identically, or (b) special-case the empty list to return `null` in `contentAsString` to preserve exact behavior. Check `ToolCallConverterTest` for existing empty-content coverage. **Prefer (b) if there is any doubt** — it guarantees identity.

**AWS AgentCore summary switch** (best-effort summary string only; lossless blob carried by `ToolCallResultContent` serialization):
```java
private String contentElementToSummaryString(Content content) {
  return switch (content) {
    case TextContent text -> text.text();
    case ObjectContent object -> contentToString(object.content());
    case DocumentContent document -> contentToString(document.document());
    case ReasoningContent reasoning -> reasoning.providerPayload() == null ? "" : contentToString(reasoning.providerPayload());
    case ProviderContent providerContent -> contentToString(providerContent.payload());
  };
}
```

**Behavior-identity constraints:** synthetic `<doc/>` `UserMessage` unchanged (same trigger, tag, preamble, in-composer); L4J tool-result text identical for every real case (resolve `""`/`null`); AWS AgentCore lossless blob byte-identical modulo the `Content`-wrapped field (BC deserializer keeps it readable); agent-instance history identical; **8.9-persisted `agentContext` still deserializes** (this commit's new obligation, discharged by `ContentJsonDeserializer`).

**Gating tests:** `ToolCallResultContentTest` + `ToolCallResultMessageBackwardCompatibilityTest` — **both already exist on the pilot with their 8.9 golden fixtures** (`backwardcompatibility/camunda-8.9/agentContext-inprocess.json`, `agentContext-camunda-document.json`, `conversation-document-payload.json`; `backwardcompatibility/aws-agentcore/agentcore-list-events.json`, `toolcallresults-blob-envelopes.json`), so we reuse them rather than author fresh. These fixtures are the golden BC net we earlier thought we lacked. **Verify they're synthetic / contain no secrets or customer data before copying them into the PR** (they're test resources authored during the pilot; confirm provenance).

**Steps:**
- [ ] **3.1** Create `ToolCallResultContent` (+ nested builder + `ContentJsonDeserializer` with the `"provider"`-inclusive discriminator set) + `ToolCallResultContentTest`. Run test → green.
- [ ] **3.2** Retype `ToolCallResultMessage.results`.
- [ ] **3.3** Split the composer (`resolveOrderedToolCallResults` + lift), leaving `createDocumentMessageForToolResults` untouched. Update `AgentConversationTurnInputComposerImplTest` (retype only).
- [ ] **3.4** Retype `AgentInstanceHistoryMapper.toolResultHistoryItem` to reuse `contentBlocks(...)`.
- [ ] **3.5** Reshape `ToolCallConverter(Impl)`; **resolve the `""`/`null` empty-content delta** (prefer returning `null` for empty). Update/confirm `ToolCallConverterTest`.
- [ ] **3.6** Retype `AwsAgentCoreConversationMapper` + `BlobEnvelope` + add `contentElementToSummaryString`.
- [ ] **3.7** Retype the remaining mechanical test updates + `TestMessagesFixture`.
- [ ] **3.8** Gather/verify the 8.9 BC fixtures; add `ToolCallResultMessageBackwardCompatibilityTest`. Run → green.
- [ ] **3.9** Build + test the module → green; e2e test-compile. Confirm `ToolCallResultDocumentAssertions` is unchanged.
- [ ] **3.10** Commit: `feat(agentic-ai): persist tool call results as structured content blocks`

---

## Task 4 — `ChatModelApi` SPI reshape (atomic)

**Deliverable:** the LangChain4J adapter is replaced by the `ChatModelApi` SPI: `supports()`-routed registry, turn-based continuation loop, sealed `StopReason`, per-provider factories, and `ProviderConfiguration` as the `ChatModelApiConfiguration`. Old `AiFrameworkAdapter`/`ChatModelFactory`/`ChatModelProvider(Registry)` layer deleted. **v1/L4J behavior identical**, including the content-filter throw.

> This commit is atomic because the old and new SPI cross-reference through the factory layer; a partial split either fails to compile or ships dead/duplicated code.

**Files — create** (under `chatmodel/`, and `chatmodel/provider/langchain4j/factory/`):
- `chatmodel/ChatModelApi.java`, `ChatModelRequest.java`, `ChatModelResult.java`, `ChatModelApiConfiguration.java`, `ChatModelApiFactory.java`, `ChatModelApiRegistry.java`, `ChatModelApiRegistryImpl.java`
- `model/message/StopReason.java`
- `chatmodel/provider/langchain4j/factory/Langchain4JChatModelApiFactory.java` (abstract) + 6 concrete: `Langchain4J{Anthropic,Bedrock,AzureOpenAi,GoogleVertexAi,OpenAi,OpenAiCompatible}ChatModelApiFactory.java`
- Tests: `chatmodel/ChatModelApiRegistryImplTest.java`, `chatmodel/provider/langchain4j/Langchain4JChatModelApiTest.java`, `chatmodel/provider/langchain4j/factory/Langchain4JChatModelApiFactoryTest.java` + 6 per-provider `*ChatModelApiFactoryTest.java`.

**Files — reshape (from the Task-1-relocated files):**
- `chatmodel/provider/langchain4j/Langchain4JAiFrameworkAdapter.java` → `Langchain4JChatModelApi.java` (`implements ChatModelApi`; `executeChatRequest` → `call(ChatModelRequest)` always returning `Completed`; constructor-injected `CloseableChatModel`, closed via `close()`).
- `chatmodel/provider/langchain4j/ChatMessageConverterImpl.java` — add `StopReason` mapping (`toStopReason(FinishReason)`, `modelId`/`messageId`). **Keep the CONTENT_FILTER throw — see below.**
- `chatmodel/provider/langchain4j/ChatModelProviderSupport.java` → move to `factory/`; fold in its factory-support delta.
- `chatmodel/provider/langchain4j/provider/AwsBedrockRuntimeAuthenticationCustomizer.java` → `factory/` (pure move).
- `agent/BaseAgentRequestHandler.java` — continuation loop (see below).
- `model/AgentConversation.java` — add `nextContinuationRound()`.
- `autoconfigure/AgenticAiConnectorsAutoConfiguration.java` — add `@Bean ChatModelApiRegistry aiAgentChatModelApiRegistry(List<ChatModelApiFactory>)`.
- `chatmodel/provider/langchain4j/configuration/AgenticAiLangchain4JChatModelConfiguration.java` — register the 6 `Langchain4JXChatModelApiFactory` beans (drop the 6 `ChatModelProvider` + registry + factory beans).
- `chatmodel/provider/langchain4j/configuration/AgenticAiLangchain4JFrameworkConfiguration.java` — drop the singleton adapter bean; keep the `@ConditionalOnProperty("camunda.connector.agenticai.framework")` gate.
- Entry points `aiagent/AiAgentFunction.java`, `AiAgentJobWorker.java` — pass `provider` (a `ProviderConfiguration`) directly as the `ChatModelApiConfiguration`; **do not create a `V1ChatModelApiConfiguration` wrapper** (it never existed on main).
- `model/request/provider/ProviderConfiguration.java` — `sealed interface ProviderConfiguration extends ChatModelApiConfiguration` (+ import). Members/methods unchanged.
- `AgentConfiguration` / execution-context records — carry a `ChatModelApiConfiguration chatModelApiConfiguration` field populated directly from `provider`.

**Files — delete:**
- `chatmodel/AiFrameworkAdapter.java`, `chatmodel/AiFrameworkChatResponse.java`, `chatmodel/provider/langchain4j/Langchain4JAiFrameworkChatResponse.java`
- `chatmodel/provider/langchain4j/ChatModelFactory.java` + `ChatModelFactoryImpl.java`
- `chatmodel/provider/langchain4j/provider/ChatModelProvider.java`, `ChatModelProviderRegistry.java`, the 6 `*ChatModelProvider.java`
- corresponding deleted tests: `Langchain4JAiFrameworkAdapterTest`, `ChatModelFactoryTest`, `ChatModelProviderRegistryTest`, the 6 `*ChatModelProviderTest`, `ChatModelProviderTestSupport` (superseded by the `factory/` siblings).

**Key API shapes:**
```java
public interface ChatModelApi extends AutoCloseable {
  ChatModelResult call(ChatModelRequest request);
  @Override void close();
  // NOTE: no capabilities() in PR1 — that method + ModelCapabilities land in PR2.
}
public record ChatModelRequest(AgentExecutionContext executionContext, ConversationSnapshot snapshot) {}
public sealed interface ChatModelResult {
  AssistantMessage assistantMessage();
  AgentMetrics metrics();
  record Completed(AssistantMessage assistantMessage, AgentMetrics metrics) implements ChatModelResult {}
  record Continuation(AssistantMessage assistantMessage, AgentMetrics metrics) implements ChatModelResult {}
}
public interface ChatModelApiConfiguration {}
public interface ChatModelApiFactory {
  boolean supports(ChatModelApiConfiguration configuration);
  ChatModelApi create(ChatModelApiConfiguration configuration);
}
public interface ChatModelApiRegistry { ChatModelApi resolve(ChatModelApiConfiguration configuration); }
// ChatModelApiRegistryImpl(List<ChatModelApiFactory>): linear supports() scan; fail loud (ERROR_CODE_FAILED_MODEL_CALL) on 0 or >1 matches.

// model/message/StopReason.java — sealed KnownStopReason|UnknownStopReason; @JsonValue/@JsonCreator; values STOP,LENGTH,TOOL_USE,CONTENT_FILTERED,GUARDRAIL,ERROR,ABORTED. Persisted on AssistantMessage; CONTENT_FILTERED drives the generic content-filter guard.
```

**`ChatModelApi` has no `capabilities()` in PR1.** `ModelCapabilities` and the `capabilities()` accessor are PR2 — nothing in PR1 consumes them. So `ChatModelApi` exposes only `call()` + `close()`, the factory constructor takes no `ModelCapabilities`, and the continuation loop below does **not** call `toolCallResultStrategy.routeToolResults(...)` (also PR2/deferred).

**Continuation loop (`BaseAgentRequestHandler.proceed`)** — shared by both connector flavors; strip the PR2 `routeToolResults` call:
```java
var workingConversation = conversation;
try (final var chatModel = chatModelApiRegistry.resolve(agentConfiguration.chatModelApiConfiguration())) {
  boolean continued;
  do {
    final var windowedSnapshot = workingConversation.window(agentConfiguration.contextWindowSize());
    final var chatResult = chatModel.call(new ChatModelRequest(executionContext, windowedSnapshot));
    // Content-filter guard — provider-agnostic, keyed on the mapped stop reason (see note below).
    // Thrown before ingest so a filtered response fails the job exactly as on main.
    if (chatResult.assistantMessage().stopReason() == StopReason.KnownStopReason.CONTENT_FILTERED) {
      throw new ConnectorException(ERROR_CODE_MODEL_RESPONSE_CONTENT_FILTERED,
          "The model response was filtered due to content restrictions");
    }
    workingConversation = workingConversation.ingest(chatResult.assistantMessage(), chatResult.metrics());
    agentInstanceClient.createHistoryForAssistantMessage(executionContext, agentInstanceKey, workingConversation.currentTurn(), OffsetDateTime.now());
    continued = chatResult instanceof ChatModelResult.Continuation;
    if (continued) {
      notifyMetrics(executionContext, workingConversation.toAgentContext(), workingConversation.currentTurnMetrics(), null, false);
      throwIfLimitsReached(workingConversation, agentConfiguration);
      workingConversation = workingConversation.nextContinuationRound();
    }
  } while (continued);
}
```
`Langchain4JChatModelApi.call()` always returns `Completed`, so the loop runs exactly once for all 6 L4J providers — identical to main's single-shot path.

**⚠️ Behavior-identity — content-filter guard, elevated to the generic loop.** Main threw `ConnectorException(ERROR_CODE_MODEL_RESPONSE_CONTENT_FILTERED, ...)` from inside the L4J adapter when `finishReason() == FinishReason.CONTENT_FILTER`. In PR1 we keep the *same observable behavior* (the job still fails on a filtered response) but implement it **once, generically**, now that finish reasons map to a neutral `StopReason`:
- `ChatMessageConverterImpl` maps `FinishReason.CONTENT_FILTER` → `StopReason.CONTENT_FILTERED` on the built `AssistantMessage` (no throw in the L4J layer).
- `BaseAgentRequestHandler.proceed` throws `ERROR_CODE_MODEL_RESPONSE_CONTENT_FILTERED` when `chatResult.assistantMessage().stopReason() == CONTENT_FILTERED`, **before ingest** (see the loop above) — so nothing is persisted and the job fails exactly as on main.

This refines the pilot (which *dropped* the guard entirely): the guard lives in the vendor-neutral layer, keyed on `CONTENT_FILTERED`, so every future provider (PR4/PR5) that maps to it inherits the guard for free. **Record this in the ADR.** Grep `ERROR_CODE_MODEL_RESPONSE_CONTENT_FILTERED` + `CONTENT_FILTER` across main's unit/e2e tests and confirm they stay green with the guard in its new home (a main unit test that asserted the adapter throws must move to asserting the *handler* throws — that is a same-PR test relocation, not an e2e assertion change).

**Factory shape (abstract):**
```java
public abstract class Langchain4JChatModelApiFactory<T extends ProviderConfiguration> implements ChatModelApiFactory {
  protected Langchain4JChatModelApiFactory(ChatMessageConverter, ToolSpecificationConverter, JsonSchemaConverter) { ... }  // no ModelCapabilities in PR1
  protected abstract String providerType();
  protected abstract CloseableChatModel createChatModel(T providerConfiguration);
  protected AgentMetrics.TokenUsage mapTokenUsage(@Nullable TokenUsage usage) { /* base: input/output only */ }
  @Override public boolean supports(ChatModelApiConfiguration c) {
    return c instanceof ProviderConfiguration p && providerType().equals(p.providerType());
  }
  @Override public ChatModelApi create(ChatModelApiConfiguration c) {
    final var pc = (ProviderConfiguration) c;
    return new Langchain4JChatModelApi(createChatModel((T) pc), chatMessageConverter, toolSpecificationConverter, jsonSchemaConverter, this::mapTokenUsage);
  }
}
```
The 6 concrete `createChatModel(...)` bodies are **byte-for-byte the main `*ChatModelProvider.createChatModel` bodies**. In Task 4 the base `mapTokenUsage` maps input/output only (no overrides yet — those are Task 5).

**Behavior-identity constraints:** each factory builds the identical L4J client/config as its main `*ChatModelProvider`; `supports()` routing is observably identical to main's map-by-discriminator (disjoint `providerType()`s); `ChatModelApiConfiguration` is an empty marker (no serialization change to `provider` input); model lifecycle changes to once-per-request (inert for single-round L4J; note in ADR).

**Gating tests:** `ChatModelApiRegistryImplTest` (0/1/>1 match); `Langchain4JChatModelApiTest` (successor to `Langchain4JAiFrameworkAdapterTest`) now asserts `FinishReason.CONTENT_FILTER` → an `AssistantMessage` with `StopReason.CONTENT_FILTERED` (no throw here); the 6 `Langchain4JXChatModelApiFactoryTest` (successors to `*ChatModelProviderTest`, same `createChatModel` assertions); and the continuation-loop tests in `JobWorkerAgentRequestHandlerTest`/`OutboundConnectorAgentRequestHandlerTest` (`proceedsThroughContinuationRoundsAsSeparatePersistedTurns`, `intermediateContinuationMetricsPushFailureDoesNotAbortTheLoop`, `throwsWhenModelCallLimitReachedBetweenContinuationRounds`) — mock `call()` returning `Continuation` then `Completed`. **The content-filter throw case (main's assertion) moves here** — a handler test that mocks `call()` returning a `CONTENT_FILTERED` assistant message and asserts `ERROR_CODE_MODEL_RESPONSE_CONTENT_FILTERED` before any ingest. `AgentConfigurationMappingTest` proves `chatModelApiConfiguration()` IS the `ProviderConfiguration` (not a wrapper).

**Steps:**
- [ ] **4.1** Create the SPI types (`ChatModelApi` with `call()` + `close()` only — **no `capabilities()`**, `ChatModelRequest`, `ChatModelResult`, `ChatModelApiConfiguration`, `ChatModelApiFactory`, `ChatModelApiRegistry(Impl)`) + `StopReason`.
- [ ] **4.2** Reshape `Langchain4JAiFrameworkAdapter` → `Langchain4JChatModelApi` (`call()` always returns `Completed`, no content-filter throw here); add the `FinishReason` → `StopReason` mapping (incl. `CONTENT_FILTER` → `CONTENT_FILTERED`) to `ChatMessageConverterImpl`.
- [ ] **4.3** Create the abstract factory + 6 concrete factories (copy `createChatModel` bodies verbatim); move `ChatModelProviderSupport` + `AwsBedrockRuntimeAuthenticationCustomizer` into `factory/`.
- [ ] **4.4** `ProviderConfiguration extends ChatModelApiConfiguration`; wire entry points to pass `provider` directly; add the registry `@Bean` to `AgenticAiConnectorsAutoConfiguration`; update both `@Configuration` classes.
- [ ] **4.5** Add `AgentConversation.nextContinuationRound()`; rewrite `BaseAgentRequestHandler.proceed` as the continuation loop **with the content-filter guard** (throw `ERROR_CODE_MODEL_RESPONSE_CONTENT_FILTERED` on `CONTENT_FILTERED` stop reason before ingest); no `routeToolResults`.
- [ ] **4.6** Delete the old layer (adapter/response/factory/provider(registry) + their tests).
- [ ] **4.7** Port/author gating tests (registry; adapter's `CONTENT_FILTER`→`CONTENT_FILTERED` mapping; handler content-filter throw; 6 factory tests; continuation-loop tests; config-mapping test).
- [ ] **4.8** Build + test the module → green (grep-confirm no `ERROR_CODE_MODEL_RESPONSE_CONTENT_FILTERED` test regressions); e2e test-compile.
- [ ] **4.9** Commit: `feat(agentic-ai): introduce the ChatModelApi provider SPI with continuation support`

---

## Task 5 — Cache + reasoning-token metrics

**Deliverable:** `AgentMetrics.TokenUsage` carries cache/reasoning dimensions, and 4 of 6 L4J factories populate them from LangChain4J's own `TokenUsage` subclasses. Old persisted state deserializes unchanged; all-zero output byte-identical to today.

**Files — modify:**
- `model/AgentMetrics.java` — `TokenUsage` gains `cacheReadTokenCount`, `cacheCreationTokenCount`, `reasoningTokenCount` (`@JsonInclude(NON_DEFAULT)`), a 2-arg constructor delegating to the 5-arg, and an updated `add()`.
- `AgentMetricsTest.java` — new cases.
- The 4 factories from Task 4 — override `mapTokenUsage`: `Langchain4JAnthropicChatModelApiFactory` (`AnthropicTokenUsage.cacheReadInputTokens/cacheCreationInputTokens`), `Langchain4JBedrockChatModelApiFactory` (`BedrockTokenUsage.cacheReadInputTokens/cacheWriteInputTokens`), `Langchain4JOpenAiChatModelApiFactory` + `Langchain4JOpenAiCompatibleChatModelApiFactory` (`OpenAiTokenUsage` via `ChatModelProviderSupport.applyOpenAiTokenUsageDetail`). Azure/Vertex: no override.

**Target `TokenUsage` shape:** the 5-field record with `@JsonInclude(NON_DEFAULT)` on the three new fields, `totalTokenCount()` = input + output (cache/reasoning are subsets, never double-counted), 2-arg ctor retained. (See `48433a0d63:.../model/AgentMetrics.java`.)

**Behavior-identity constraints:** `@JsonInclude(NON_DEFAULT)` keeps zero-valued fields out of persisted JSON → old `agentContext` deserializes and all-zero (Azure/Vertex, and any provider not returning detail) serializes byte-identically. The engine history API (`AgentInstanceHistoryMetrics`) has no cache/reasoning fields, so nothing new surfaces there. The 4 enriched providers now emit real non-zero counts internally — an *additive* improvement, not observable via any changed assertion.

**Gating tests:** `AgentMetricsTest` — aggregation with cache/reasoning as subsets; Jackson round-trip proving zero-omission + old-state deserialization. **Do NOT add** the real-API smoke helpers (`hasReasoningTokensGreaterThanZero` etc.) — no PR1 caller, dead until PR4/5.

**Steps:**
- [ ] **5.1** Add the 3 fields + 2-arg ctor + `add()` to `AgentMetrics.TokenUsage`.
- [ ] **5.2** Add `AgentMetricsTest` cases (aggregation subset, zero-omission round-trip, old-state deserialize). Run → green.
- [ ] **5.3** Add `mapTokenUsage` overrides to the 4 factories; extend the 4 factory tests to assert the mapped detail.
- [ ] **5.4** Build + test the module → green; e2e test-compile.
- [ ] **5.5** Commit: `feat(agentic-ai): record prompt-cache and reasoning token usage`

---

## Exit criteria (whole PR1)

- Full `connector-agentic-ai` unit + e2e suite green with **no e2e test changes**; e2e module test-compiles.
- No references to `aiagent.framework.*` remain; no `V1ChatModelApiConfiguration` wrapper introduced; no `capabilities`/`multimodal`/`v2`/native/`transport` code added.
- Content-filter behavior preserved (guard elevated to the generic loop, keyed on `CONTENT_FILTERED`); `""`/`null` empty-tool-result delta resolved toward `null` to preserve identity.
- 8.9-persisted `agentContext` fixtures deserialize (reused from the pilot).

## ADR (ships with PR1, not superpowers docs)

Write an "Own the LLM layer" ADR covering: the `ChatModelApi` SPI + `supports()` registry (plug-in point), turn-based continuation (`Completed | Continuation`), the content-block message model (incl. `ToolCallResultContent` `List<Content>` shape and its 8.9 BC), the `AutoCloseable` per-request model lifecycle, and the stated non-goals (capabilities, v2, native providers, tool-result routing — all later PRs). Note the intentional design points: the content-filter guard is enforced generically in `BaseAgentRequestHandler` off the mapped `CONTENT_FILTERED` stop reason (vendor-neutral, so every provider inherits it); and cache/reasoning metrics are live for 4/6 L4J providers.

## Self-review notes (done during planning)

- **Coverage:** every roadmap PR1 bullet maps to a task (relocation→T1, blocks→T2, tool-result shape→T3, SPI/factories/config→T4, metrics→T5).
- **Resolved decisions:** `ChatModelApi.capabilities()` dropped for PR1 (PR2 adds it); `""`/`null` empty content → return `null`; content-filter guard elevated to the generic loop keyed on `StopReason.CONTENT_FILTERED`.
- **Type consistency:** `ProviderConfiguration` (not `V1ProviderConfiguration`), `model.request.provider` (not `.v1`), `aiagent.chatmodel.*` post-T1 — used consistently; the pilot's baked-in PR3 rename must be translated back everywhere.
