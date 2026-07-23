# Chunk 5 — `ToolCallResultStrategy` (capability-keyed routing) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this chunk task-by-task. Steps use checkbox (`- [ ]`) syntax. Implementation subagents run on **Sonnet** and never run git ops.

**Parent plan:** `docs/superpowers/plans/2026-07-08-vertical-pilot-own-llm-layer.md` (chunk table row **C5**).
**Spec (design of record):** `docs/superpowers/specs/2026-07-08-vertical-pilot-own-llm-layer-design.md` §6–§8.
**Sub-issue:** #7232 (routing half). **Depends on:** C3 (`ModelCapabilities`/`Modality`, `ChatModelApi.capabilities()`), C4 (`ToolCallResultContent`, `ToolCallResultMessage.results: List<ToolCallResultContent>`).

## Ratified design (Option 2 — self-describing tool-result content; do not re-litigate)

The persisted **source of truth** for tool-result documents is the self-describing `ToolCallResultMessage`: the composer lifts extracted documents into `DocumentContent` **inside** each `ToolCallResultContent.content`. `ToolCallResultContent`, its `from(...)`, and C4's BC proxy-builder/deserializer are **unchanged** (no gateway coupling on the domain type; C4's sign-off stands). The strategy is a **sent-only** transform on the already-windowed snapshot; it **persists nothing**.

- **LIFT** happens in the **composer** (`AgentConversationTurnInputComposerImpl`), which already holds `GatewayToolHandlerRegistry` + `ToolCallResultDocumentExtractor`. At compose time the raw tool-return content is live (typed MCP/A2A POJOs intact), so gateway-aware extraction still fires. Each `ToolCallResultContent` = today's `ToolCallResultContent.from(rawResult)` content **plus** a `DocumentContent` per extracted document, **deduped by `DocumentReference`** (so a bare-`Document` result already lifted to `DocumentContent` is not double-added). The eager `createDocumentMessageForToolResults(...)` synthetic-`UserMessage` path is **removed** from `compose()`.
- **STRATEGY** (`ToolCallResultStrategy`, new package `...framework.multimodal`) runs in `BaseAgentRequestHandler.proceed()` in the order `snapshot = workingConversation.window(contextWindowSize)` → `sent = strategy.apply(snapshot, chatModel.capabilities())` → `chatModel.call(new ChatModelRequest(executionContext, sent))`. Per **document** (keyed by `DocumentModality` MIME→`Modality`):
  - **NATIVE** (`toolResult` modality supports the doc's `Modality`): leave the `DocumentContent` inline in the tool-result block.
  - **FALLBACK** (bridge `toolResult:[TEXT]`, or modality unsupported): **strip** those `DocumentContent` entries from the tool-result block **and** insert a transient `<doc/>` XML synthetic `UserMessage` at the matching position — byte-identical LLM string (`TOOL_CALL_DOCUMENTS_PREAMBLE` + `DocumentReferenceXmlTag.toXml()` + `DocumentContent` pairs, tagged `METADATA_TOOL_CALL_DOCUMENTS`) — all inside the returned transformed `ConversationSnapshot`. The strip is required because the L4J bridge (`ToolCallConverterImpl.contentElementAsString`) serializes a tool-result `DocumentContent` as an extra document-reference line; without stripping, the bridge would emit that extra line and break byte-identity.
- **NO STORED-DATA BC** for this shape: documents-in-tool-results is 8.10-only and never shipped in a stable release, so there is **no** "already-rendered synthetic message" legacy branch. The only hard BC is 8.9-persisted data (handled by C4's read path, which has no docs-in-tool-results); C4's 8.9 golden fixtures stay green.
- **USER/EVENT-message documents:** the composer's eager inline embedding stays **untouched** (byte-identical). The strategy only **validates** non-tool-call-document `UserMessage` document modalities against `userMessage` caps and **fails loud** on unsupported: `ConnectorException(ERROR_CODE_FAILED_MODEL_CALL, ...)` naming the reference, required modality, and supported set. No synthesis fallback for user/event messages.
- **Interface refinement (A):** under Option 2 `Result.syntheticContextMessages` would always be empty (the strategy persists nothing; the synthetic message lives only inside the returned snapshot). We therefore drop the `Result` wrapper: **`apply(...)` returns the transformed `ConversationSnapshot` directly**. This refines the spec's C5 interface (parent plan §C5 listed `Result apply(...)`); safe because no other chunk consumes `Result`.

## Goal

Introduce a single-pass, capability-keyed `ToolCallResultStrategy` that transforms the windowed `ConversationSnapshot` after chat-model resolution and before the model call, routing tool-result documents to native-inline blocks or the byte-identical `<doc/>` synthetic fallback per the resolved `ModelCapabilities`, and failing loud on unsupported user/event-message document modalities. Move the gateway-aware document lift into the composer (self-describing tool-result content) and remove the eager synthetic-message extraction. On the LangChain4j bridge (`toolResult:[TEXT]`) every tool-result document takes the fallback → **byte-identical to today**, asserted via the e2e module.

## Architecture

The strategy is a dependency-free, provider-neutral SPI collaborator in a new package `io.camunda.connector.agenticai.aiagent.provider.multimodal`, wired as a Spring bean and injected into `BaseAgentRequestHandler` (both subclass constructors + autoconfiguration beans). It reads `DocumentContent` **directly** from the self-describing `ToolCallResultContent.content` (no `GatewayToolHandlerRegistry` — extraction/gateway-awareness lives in the composer). `DocumentModality` maps a document's MIME type to a `Modality` bucket; unknown/blank → `DOCUMENT` (conservative, gates to fallback).

## C5 Global Constraints (copied verbatim from the parent plan's "Global Constraints")

- **Backward compatibility on 8.9-persisted stored data is the top priority.** The only stored-format *shape* change is the tool-call-result model (Chunk 4). A fast-forward migration (lift on read, may re-persist upgraded) is acceptable **only if it never silently fails or drops data** — unmappable legacy shapes fail loud. Proven by golden 8.9 fixtures across in-process, Camunda-document, and AWS-AgentCore stores.
- Existing **v1 connector types and element templates are untouched** and keep running on the L4J bridge.
- The **single `BaseAgentRequestHandler` is enriched**, never forked. No parallel handler.
- Only `framework/langchain4j/**` may import `dev.langchain4j.*`.
- **v2 names are locked:** Task ET `io.camunda.connectors.agenticai.ai-agent-task.v2` / type `io.camunda.agenticai:aiagent:task:2`; Sub-process ET `io.camunda.connectors.agenticai.ai-agent-subprocess.v2` / type `io.camunda.agenticai:aiagent:subprocess:2`.
- **Auth:** fixed where the provider dictates (Anthropic direct = API key, bedrock = AWS creds, OpenAI direct = API key); only the OpenAI `compatible` backend gets an extensible dropdown (`none`/`apiKey` now, OAuth2-ready). No OAuth on Anthropic/Bedrock.
- **Keep the separate e2e module green (compile + pass) with every chunk.** Small smoke test per wire format; full variant coverage is a follow-up.
- Coherent-per-chunk commits; **never push without explicit approval**; implementation subagents run on **Sonnet** and never run git ops.

## Build / Test reference (from the parent plan; run with `dangerouslyDisableSandbox: true`)

```bash
# Module build (regenerates element templates during compile — commit the JSON diff)
mvn -q -Dmaven.build.cache.enabled=false -f connectors/agentic-ai/pom.xml clean install
# Module unit tests, single class
mvn -q -Dmaven.build.cache.enabled=false -f connectors/agentic-ai/pom.xml test -Dtest=<ClassName>
# Separate e2e module (repo root), single test; -am to rebuild deps
mvn -q -Dmaven.build.cache.enabled=false -am -pl connectors-e2e-test/connectors-e2e-test-agentic-ai test -Dtest=<ClassName>
```
Module base package: `io.camunda.connector.agenticai`; module root `connectors/agentic-ai/connector-agentic-ai`. E2E harness needs `element-templates-cli` on PATH. C5 changes **no** element templates (no JSON diff expected).

## Consumed interfaces (already present — no C5 task)

- **`ChatModelApi.capabilities()`** (C3) returns `ModelCapabilities`. The **L4J bridge already returns the uniform conservative profile** — confirmed in `Langchain4JChatModelApi.BRIDGE_CAPABILITIES`: `userMessageModalities=[TEXT,IMAGE,DOCUMENT]`, `toolResultModalities=[TEXT]`, `assistantMessageModalities=[TEXT]`, all flags false. C5 only consumes it.
- `ModelCapabilities` (record) + `ModelCapabilities.Modality { TEXT, IMAGE, DOCUMENT, AUDIO, VIDEO }` (C3).

## File structure map

**Create (main):**
- `.../aiagent/framework/multimodal/DocumentModality.java`
- `.../aiagent/framework/multimodal/ToolCallResultStrategy.java` (interface; hosts the shared `TOOL_CALL_DOCUMENTS_PREAMBLE` constant)
- `.../aiagent/framework/multimodal/CapabilityAwareToolCallResultStrategy.java` (the single `*Impl`)

**Create (test):**
- `.../src/test/java/io/camunda/connector/agenticai/aiagent/framework/multimodal/DocumentModalityTest.java`
- `.../src/test/java/io/camunda/connector/agenticai/aiagent/framework/multimodal/CapabilityAwareToolCallResultStrategyTest.java`
- `.../src/test/java/io/camunda/connector/agenticai/aiagent/framework/multimodal/ToolResultDocumentWindowingTest.java`

**Modify (main):**
- `.../aiagent/agent/AgentConversationTurnInputComposerImpl.java` — lift extracted documents into each `ToolCallResultContent.content` (gateway-aware, deduped by `DocumentReference`); remove `createDocumentMessageForToolResults(...)` and its call; drop the now-unused `TOOL_CALL_DOCUMENTS_PREAMBLE` (moved to `ToolCallResultStrategy`). **Keep** `documentExtractor`, `createDocumentPairs`, `EVENT_DOCUMENTS_PREAMBLE` and the whole `createEventMessage(...)` path unchanged (events keep eager inline embedding).
- `.../aiagent/agent/BaseAgentRequestHandler.java` — add a `ToolCallResultStrategy` field/ctor param; inside the continuation loop, transform the windowed snapshot via the strategy and feed the result into `ChatModelRequest`.
- `.../aiagent/agent/JobWorkerAgentRequestHandler.java` and `.../aiagent/agent/OutboundConnectorAgentRequestHandler.java` — thread the new ctor param.
- `.../autoconfigure/AgenticAiConnectorsAutoConfiguration.java` — add the `aiAgentToolCallResultStrategy()` bean; inject it into both handler beans.

**Do NOT modify:** `ToolCallResultContent`, `ToolCallResultMessage`, C4 deserializers/BC fixtures, `MessageWindowFilter` (see "Memory window / eviction"), `AgentConversation` (Option 2 persists nothing new).

**Modify (test):**
- `.../aiagent/agent/AgentConversationTurnInputComposerImplTest.java` — update `toolResultTurn_resultsContainDocuments_emitsToolCallDocumentMessage` and `waitForToolResults_toolAndEventDocuments_emitsMessagesInOrder` to the self-describing shape (no tool-call synthetic message; `DocumentContent` now appended inside the `ToolCallResultContent`). Event assertions stay.
- `.../aiagent/agent/OutboundConnectorAgentRequestHandlerTest.java` / `.../aiagent/agent/JobWorkerAgentRequestHandlerTest.java` — pass a strategy to the ctor (real `CapabilityAwareToolCallResultStrategy` is identity when there are no docs); add a proceed-path test asserting the strategy-transformed snapshot reaches `chatModel.call(...)`.
- e2e (keep green, no new file): `.../AiAgentConnectorToolCallingTests.java` + `.../ToolCallResultDocumentAssertions.java` — the byte-identical guard. Update `ToolCallResultDocumentAssertions.EXTRACTED_DOCUMENTS_PREAMBLE` to reference `ToolCallResultStrategy.TOOL_CALL_DOCUMENTS_PREAMBLE` (single shared constant, per ratified point 5).

## Memory window / eviction (verified against `MessageWindowFilter.apply`; filter is NOT modified in C5)

Order in `proceed()`: `window(...)` runs on the **real, self-describing** persisted messages, **then** the strategy transforms the windowed snapshot. Confirmed from `MessageWindowFilter.apply`:

1. **Atomic eviction is preserved.** A `ToolCallResultMessage` (now possibly carrying `DocumentContent`) is still evicted together with its originating `AssistantMessage` that `hasToolCalls()` (the loop removes follow-up `ToolCallResultMessage`s after evicting such an assistant message). A tool-result document therefore evicts atomically with its turn — it cannot be sent to the model detached from its tool call.
2. **Window accounting is unchanged vs today.** A `ToolCallResultMessage` is not an `isToolCallDocumentMessage` (that predicate matches only a `UserMessage` tagged `METADATA_TOOL_CALL_DOCUMENTS`), so it counts as exactly **1** window slot whether or not it now carries `DocumentContent`. No change to the effective count.
3. **The fallback synthetic message never flows through the filter.** It is created **post-window** inside the strategy and never persisted, so `MessageWindowFilter` never sees it. Its `isToolCallDocumentMessage` special-casing (not-counted + follow-up-doc eviction) becomes **dead code for new data**, but is **left untouched**: harmless, and still guards any pre-existing persisted data. Removing it is out of C5 scope.

Task 5 asserts this interaction (doc survives while its turn is in-window; gone once the turn is evicted; window count matches today).

---

## Task 1 — `DocumentModality` (MIME → `Modality`)

**Files:** create `DocumentModality.java` + `DocumentModalityTest.java`.

**Interfaces**
- Consumes: `ModelCapabilities.Modality`; `io.camunda.connector.api.document.Document`/`DocumentMetadata`.
- Produces: `static Modality fromContentType(@Nullable String contentType)`; `static Modality fromDocument(Document document)`.

Mapping (spec §7): `image/* → IMAGE`; `audio/* → AUDIO`; `video/* → VIDEO`; `application/pdf → DOCUMENT`; `text/*` + `application/json` + `application/xml` + `application/yaml`/`application/x-yaml` + `+json`/`+xml` suffixes `→ TEXT`; everything else incl. null/blank `→ DOCUMENT`.

- [ ] **Step 1 — Failing test.** Create `DocumentModalityTest.java`:

```java
package io.camunda.connector.agenticai.aiagent.provider.multimodal;

import static io.camunda.connector.agenticai.aiagent.provider.capabilities.ModelCapabilities.Modality;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DocumentModalityTest {

	@ParameterizedTest
	@CsvSource({
		"image/png, IMAGE",
		"image/jpeg, IMAGE",
		"application/pdf, DOCUMENT",
		"text/plain, TEXT",
		"text/csv, TEXT",
		"application/json, TEXT",
		"application/xml, TEXT",
		"application/yaml, TEXT",
		"application/vnd.api+json, TEXT",
		"audio/mpeg, AUDIO",
		"video/mp4, VIDEO",
		"application/octet-stream, DOCUMENT",
		"application/vnd.ms-excel, DOCUMENT"
	})
	void mapsMimeToModality(String contentType, Modality expected) {
		assertThat(DocumentModality.fromContentType(contentType)).isEqualTo(expected);
	}

	@Test
	void stripsParametersAndIsCaseInsensitive() {
		assertThat(DocumentModality.fromContentType("Text/Plain; charset=UTF-8")).isEqualTo(Modality.TEXT);
	}

	@Test
	void defaultsToDocumentForNullOrBlank() {
		assertThat(DocumentModality.fromContentType(null)).isEqualTo(Modality.DOCUMENT);
		assertThat(DocumentModality.fromContentType("  ")).isEqualTo(Modality.DOCUMENT);
	}
}
```

- [ ] **Step 2 — Run to confirm failure.**
`mvn -q -Dmaven.build.cache.enabled=false -f connectors/agentic-ai/pom.xml test -Dtest=DocumentModalityTest` → FAIL (class missing).

- [ ] **Step 3 — Minimal impl.** Create `DocumentModality.java`:

```java
package io.camunda.connector.agenticai.aiagent.provider.multimodal;

import io.camunda.connector.agenticai.aiagent.provider.capabilities.ModelCapabilities.Modality;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

/**
 * Maps a content's MIME type to the {@link Modality} bucket a provider ingests it as. Unknown/blank
 * types map conservatively to {@link Modality#DOCUMENT} (which gates to the synthetic fallback).
 */
public final class DocumentModality {

	private DocumentModality() {
	}

	public static Modality fromDocument(Document document) {
		final DocumentMetadata metadata = document.metadata();
		return fromContentType(metadata != null ? metadata.getContentType() : null);
	}

	public static Modality fromContentType(@Nullable String contentType) {
		if (contentType == null || contentType.isBlank()) {
			return Modality.DOCUMENT;
		}
		var mime = contentType.toLowerCase(Locale.ROOT).trim();
		final int paramIdx = mime.indexOf(';');
		if (paramIdx >= 0) {
			mime = mime.substring(0, paramIdx).trim();
		}
		if (mime.startsWith("image/")) {
			return Modality.IMAGE;
		}
		if (mime.startsWith("audio/")) {
			return Modality.AUDIO;
		}
		if (mime.startsWith("video/")) {
			return Modality.VIDEO;
		}
		if (mime.equals("application/pdf")) {
			return Modality.DOCUMENT;
		}
		if (mime.startsWith("text/")
			|| mime.equals("application/json")
			|| mime.equals("application/xml")
			|| mime.equals("application/yaml")
			|| mime.equals("application/x-yaml")
			|| mime.endsWith("+json")
			|| mime.endsWith("+xml")) {
			return Modality.TEXT;
		}
		return Modality.DOCUMENT;
	}
}
```

- [ ] **Step 4 — Run to pass.** Same command as Step 2 → PASS. (No commit — C5 is one commit at the end.)

---

## Task 2 — Composer: self-describing tool-result content + remove eager synthetic

**Files:** modify `AgentConversationTurnInputComposerImpl.java`; update `AgentConversationTurnInputComposerImplTest.java`.

**Interfaces**
- Consumes: existing `documentExtractor` (`ToolCallResultDocumentExtractor`, gateway-aware, returns `List<ToolCallDocuments>` grouped by tool-call id/name), `ToolCallResultContent.from(...)` + `withContent(...)`, `DocumentContent`.
- Produces: `ToolCallResultMessage` whose each `ToolCallResultContent.content` = today's lift **plus** deduped `DocumentContent` per extracted document.

- [ ] **Step 1 — Update composer tests to the new shape (red).** In `AgentConversationTurnInputComposerImplTest`:
  - `toolResultTurn_resultsContainDocuments_emitsToolCallDocumentMessage`: assert `messages` has size **1** (only the `ToolCallResultMessage`; no synthetic `UserMessage`). Weather result content becomes `[ObjectContent(map), DocumentContent(weatherDoc)]`; dateTime result stays `[TextContent("15:00")]`:
    ```java
    var messages = ((CompositionResult.NextTurn) result).messages();
    assertThat(messages).hasSize(1);
    var trm = (ToolCallResultMessage) messages.getFirst();
    assertThat(trm.results())
        .satisfiesExactly(
            weather ->
                assertThat(weather.content())
                    .containsExactly(
                        ObjectContent.objectContent(
                            Map.of("result", "Sunny", "attachment", weatherDoc)),
                        DocumentContent.documentContent(weatherDoc)),
            dateTime ->
                assertThat(dateTime.content())
                    .containsExactly(TextContent.textContent("15:00")));
    ```
  - `waitForToolResults_toolAndEventDocuments_emitsMessagesInOrder`: now `messages` has size **2** — `messages.get(0)` the `ToolCallResultMessage` (getWeather result content `[ObjectContent(Map.of("file", toolDoc)), DocumentContent(toolDoc)]`), `messages.get(1)` the event `UserMessage` (unchanged: `EVENT_DOCUMENTS_PREAMBLE` + `DocumentContent(eventDoc)`, not tagged `METADATA_TOOL_CALL_DOCUMENTS`). Remove the old tool-call synthetic block.

- [ ] **Step 2 — Run to confirm failure.**
`mvn -q -Dmaven.build.cache.enabled=false -f connectors/agentic-ai/pom.xml test -Dtest=AgentConversationTurnInputComposerImplTest` → FAIL (composer still emits synthetic; `DocumentContent` not yet appended).

- [ ] **Step 3 — Implement the lift + remove eager synthetic.** In `compose()`, replace the `results.stream().map(ToolCallResultContent::from).toList()` mapping and delete the `createDocumentMessageForToolResults(results)` call + its `if`:

```java
final var results = orderedToolCallResults.get();
final var toolCallResultMessage =
    ToolCallResultMessage.builder()
        .results(toSelfDescribingContents(results))
        .metadata(defaultMessageMetadata())
        .build();
messages.add(toolCallResultMessage);
messages.addAll(eventMessages);
```

Add the helper (gateway-aware extraction on the LIVE raw results, dedup by `DocumentReference`):

```java
private List<ToolCallResultContent> toSelfDescribingContents(List<ToolCallResult> results) {
  final var documentsByToolCallId =
      documentExtractor.extractDocuments(results).stream()
          .collect(
              Collectors.toMap(
                  ToolCallResultDocumentExtractor.ToolCallDocuments::toolCallId,
                  ToolCallResultDocumentExtractor.ToolCallDocuments::documents));

  return results.stream()
      .map(
          result -> {
            final var base = ToolCallResultContent.from(result);
            final var documents = documentsByToolCallId.getOrDefault(result.id(), List.of());
            if (documents.isEmpty()) {
              return base;
            }
            final var existingRefs =
                base.content().stream()
                    .filter(DocumentContent.class::isInstance)
                    .map(c -> ((DocumentContent) c).document().reference())
                    .collect(Collectors.toCollection(HashSet::new));
            final var content = new ArrayList<Content>(base.content());
            for (var document : documents) {
              if (existingRefs.add(document.reference())) {
                content.add(DocumentContent.documentContent(document));
              }
            }
            return base.withContent(content);
          })
      .toList();
}
```

Then delete the now-unused `createDocumentMessageForToolResults(...)` method and the `TOOL_CALL_DOCUMENTS_PREAMBLE` constant (moved to `ToolCallResultStrategy`). **Keep** `documentExtractor`, `createDocumentPairs`, `EVENT_DOCUMENTS_PREAMBLE`, and `createEventMessage(...)`. Add `HashSet` import.

> `result.id()` is non-null on this branch (results are matched to `toolCalls`, which have ids; cancelled results carry the tool-call id). If NullAway flags the map key, guard with `Objects.requireNonNullElse(result.id(), "")` on both the collector key and the `getOrDefault` lookup.

- [ ] **Step 4 — Run to pass.** Same command as Step 2 → PASS.

---

## Task 3 — `ToolCallResultStrategy` SPI + `CapabilityAwareToolCallResultStrategy`

**Files:** create `ToolCallResultStrategy.java`, `CapabilityAwareToolCallResultStrategy.java`, `CapabilityAwareToolCallResultStrategyTest.java`.

**Interfaces**
- Consumes: `ConversationSnapshot`, `ModelCapabilities` (+ `.Modality`), `Message`/`UserMessage`/`ToolCallResultMessage`, `ToolCallResultContent` (+ `withContent`), `Content`/`DocumentContent`/`TextContent`, `DocumentReferenceXmlTag`, `DocumentModality` (Task 1), `Document`, `ConnectorException` + `AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL`.
- Produces (refined interface — returns the transformed snapshot directly):

```java
public interface ToolCallResultStrategy {

  /** Shared with the e2e assertion helper so the fallback preamble cannot drift. */
  String TOOL_CALL_DOCUMENTS_PREAMBLE =
      "Documents extracted from tool calls (<doc /> tag + content pair):";

  ConversationSnapshot apply(ConversationSnapshot snapshot, ModelCapabilities capabilities);
}
```

**Behavior (single pass over `snapshot.messages()`):**
- `UserMessage` not tagged `METADATA_TOOL_CALL_DOCUMENTS`: validate every `DocumentContent`'s modality against `capabilities.userMessageModalities()`; **fail loud** if unsupported; else pass through unchanged.
- `ToolCallResultMessage`: per `ToolCallResultContent`, split `DocumentContent` into **supported** (`toolResultModalities` contains its `Modality`) vs **unsupported**. Keep supported inline + all non-`DocumentContent`; **strip** unsupported. Collect unsupported docs (with the result's `id`/`name`). If any unsupported doc exists across the message, append one synthetic `UserMessage` (preamble + `(<doc/> tag, DocumentContent)` pairs, tagged, `timestamp` metadata) **immediately after** the (stripped) `ToolCallResultMessage`.
- Anything else: pass through.

- [ ] **Step 1 — Write the SPI interface** `ToolCallResultStrategy.java` (above).

- [ ] **Step 2 — Failing test.** Create `CapabilityAwareToolCallResultStrategyTest.java`:

```java
package io.camunda.connector.agenticai.aiagent.provider.multimodal;

import static io.camunda.connector.agenticai.aiagent.provider.capabilities.ModelCapabilities.Modality;
import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.aiagent.provider.capabilities.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CapabilityAwareToolCallResultStrategyTest {

	private static ModelCapabilities caps(List<Modality> toolResult, List<Modality> userMessage) {
		return ModelCapabilities.builder()
			.userMessageModalities(userMessage)
			.toolResultModalities(toolResult)
			.assistantMessageModalities(List.of(Modality.TEXT))
			.build();
	}

	private static final ModelCapabilities BRIDGE_CAPS =
		caps(List.of(Modality.TEXT), List.of(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT));
	private static final ModelCapabilities NATIVE_DOC_CAPS =
		caps(
			List.of(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT),
			List.of(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT));

	private final DocumentFactoryImpl documentFactory =
		new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);
	private final ToolCallResultStrategy strategy = new CapabilityAwareToolCallResultStrategy();

	private Document doc(String content, String contentType, String fileName) {
		return documentFactory.create(
			DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
				.contentType(contentType)
				.fileName(fileName)
				.build());
	}

	private ConversationSnapshot snapshot(Message... messages) {
		return new ConversationSnapshot(List.of(messages), List.of());
	}

	/** Mirrors the composer's self-describing lift: from(raw) content + appended DocumentContent. */
	private ToolCallResultMessage toolResult(String id, String name, Object rawContent, Document... docs) {
		final var base =
			ToolCallResultContent.from(
				ToolCallResult.builder().id(id).name(name).content(rawContent).build());
		final var content = new ArrayList<>(base.content());
		for (var d : docs) {
			content.add(DocumentContent.documentContent(d));
		}
		return ToolCallResultMessage.builder().results(List.of(base.withContent(content))).build();
	}

	@Test
	void unsupportedToolResultModality_stripsDocAndInsertsSyntheticFallback() {
		var pdf = doc("pdf-bytes", "application/pdf", "report.pdf");
		var trm = toolResult("call_1", "getReport", Map.of("k", "v"), pdf);

		var out = strategy.apply(snapshot(trm), BRIDGE_CAPS).messages();

		assertThat(out).hasSize(2);
		var strippedTrm = (ToolCallResultMessage) out.get(0);
		assertThat(strippedTrm.results().getFirst().content())
			.noneMatch(DocumentContent.class::isInstance)
			.anyMatch(ObjectContent.class::isInstance); // object content preserved
		var synthetic = (UserMessage) out.get(1);
		assertThat(synthetic.metadata()).containsEntry(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);
		assertThat(synthetic.content().getFirst())
			.isEqualTo(textContent(ToolCallResultStrategy.TOOL_CALL_DOCUMENTS_PREAMBLE));
		assertThat(synthetic.content()).contains(DocumentContent.documentContent(pdf));
	}

	@Test
	void supportedToolResultModality_keepsDocInlineAndSynthesizesNothing() {
		var pdf = doc("pdf-bytes", "application/pdf", "report.pdf");
		var trm = toolResult("call_1", "getReport", Map.of("k", "v"), pdf);

		var out = strategy.apply(snapshot(trm), NATIVE_DOC_CAPS).messages();

		assertThat(out).hasSize(1);
		var keptTrm = (ToolCallResultMessage) out.getFirst();
		assertThat(keptTrm.results().getFirst().content())
			.contains(DocumentContent.documentContent(pdf));
	}

	@Test
	void mixedModalities_stripsOnlyUnsupportedDocs() {
		var pdf = doc("pdf", "application/pdf", "report.pdf");
		var png = doc("png", "image/png", "chart.png");
		var trm = toolResult("c1", "t", Map.of("k", "v"), pdf, png);

		// toolResult supports IMAGE but not DOCUMENT
		var out =
			strategy
				.apply(snapshot(trm), caps(List.of(Modality.TEXT, Modality.IMAGE), List.of(Modality.TEXT)))
				.messages();

		assertThat(out).hasSize(2);
		var strippedTrm = (ToolCallResultMessage) out.get(0);
		assertThat(strippedTrm.results().getFirst().content())
			.contains(DocumentContent.documentContent(png)) // image kept inline
			.doesNotContain(DocumentContent.documentContent(pdf)); // pdf stripped
		assertThat(((UserMessage) out.get(1)).content())
			.contains(DocumentContent.documentContent(pdf))
			.doesNotContain(DocumentContent.documentContent(png));
	}

	@Test
	void userMessageDoc_unsupportedModality_failsLoud() {
		var pdf = doc("pdf", "application/pdf", "report.pdf");
		var userMessage =
			UserMessage.builder().content(List.of(DocumentContent.documentContent(pdf))).build();

		assertThatThrownBy(
			() ->
				strategy.apply(
					snapshot(userMessage), caps(List.of(Modality.TEXT), List.of(Modality.TEXT))))
			.isInstanceOf(ConnectorException.class)
			.hasMessageContaining("DOCUMENT")
			.hasMessageContaining("report.pdf");
	}
}
```

- [ ] **Step 3 — Run to confirm failure.**
`mvn -q -Dmaven.build.cache.enabled=false -f connectors/agentic-ai/pom.xml test -Dtest=CapabilityAwareToolCallResultStrategyTest` → FAIL (impl missing).

- [ ] **Step 4 — Minimal impl.** Create `CapabilityAwareToolCallResultStrategy.java`:

```java
package io.camunda.connector.agenticai.aiagent.provider.multimodal;

import static io.camunda.connector.agenticai.aiagent.agent.AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL;
import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;

import io.camunda.connector.agenticai.aiagent.provider.capabilities.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.message.DocumentReferenceXmlTag;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

public class CapabilityAwareToolCallResultStrategy implements ToolCallResultStrategy {

	private record FallbackGroup(
		@Nullable String toolCallId, @Nullable String toolCallName, List<Document> documents) {
	}

	@Override
	public ConversationSnapshot apply(ConversationSnapshot snapshot, ModelCapabilities capabilities) {
		final var out = new ArrayList<Message>(snapshot.messages().size());
		for (Message message : snapshot.messages()) {
			switch (message) {
				case UserMessage userMessage when !isToolCallDocumentMessage(userMessage) -> {
					validateUserMessageModalities(userMessage, capabilities);
					out.add(userMessage);
				}
				case ToolCallResultMessage trm -> routeToolResult(trm, capabilities, out);
				default -> out.add(message);
			}
		}
		return new ConversationSnapshot(out, snapshot.toolDefinitions());
	}

	private void routeToolResult(
		ToolCallResultMessage trm, ModelCapabilities capabilities, List<Message> out) {
		final var supported = capabilities.toolResultModalities();
		final var newResults = new ArrayList<ToolCallResultContent>(trm.results().size());
		final var fallbackGroups = new ArrayList<FallbackGroup>();
		boolean stripped = false;

		for (ToolCallResultContent result : trm.results()) {
			final var kept = new ArrayList<Content>(result.content().size());
			final var unsupported = new ArrayList<Document>();
			for (Content content : result.content()) {
				if (content instanceof DocumentContent dc
					&& !supported.contains(DocumentModality.fromDocument(dc.document()))) {
					unsupported.add(dc.document());
				} else {
					kept.add(content);
				}
			}
			if (unsupported.isEmpty()) {
				newResults.add(result);
			} else {
				stripped = true;
				newResults.add(result.withContent(kept));
				fallbackGroups.add(new FallbackGroup(result.id(), result.name(), unsupported));
			}
		}

		out.add(stripped ? trm.withResults(newResults) : trm);
		if (!fallbackGroups.isEmpty()) {
			out.add(buildSyntheticMessage(fallbackGroups));
		}
	}

	private UserMessage buildSyntheticMessage(List<FallbackGroup> groups) {
		final var content = new ArrayList<Content>();
		content.add(textContent(TOOL_CALL_DOCUMENTS_PREAMBLE));
		for (var group : groups) {
			for (var document : group.documents()) {
				content.add(
					textContent(
						DocumentReferenceXmlTag.from(document, group.toolCallId(), group.toolCallName())
							.toXml()));
				content.add(DocumentContent.documentContent(document));
			}
		}
		final var metadata = new HashMap<String, Object>(defaultMessageMetadata());
		metadata.put(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);
		return UserMessage.builder().content(content).metadata(metadata).build();
	}

	private void validateUserMessageModalities(UserMessage message, ModelCapabilities capabilities) {
		for (Content content : message.content()) {
			if (content instanceof DocumentContent dc) {
				final var modality = DocumentModality.fromDocument(dc.document());
				if (!capabilities.userMessageModalities().contains(modality)) {
					throw new ConnectorException(
						ERROR_CODE_FAILED_MODEL_CALL,
						"Document '%s' requires modality %s which the model does not support for user messages (supported: %s)."
							.formatted(
								dc.document().reference(), modality, capabilities.userMessageModalities()));
				}
			}
		}
	}

	private boolean isToolCallDocumentMessage(UserMessage message) {
		return message.metadata() != null
			&& Boolean.TRUE.equals(message.metadata().get(UserMessage.METADATA_TOOL_CALL_DOCUMENTS));
	}

	private Map<String, Object> defaultMessageMetadata() {
		return Map.of("timestamp", ZonedDateTime.now());
	}
}
```

> Verify the generated `withContent(List<Content>)` (on `ToolCallResultContentBuilder.With`) and `withResults(List<ToolCallResultContent>)` (on `ToolCallResultMessageBuilder.With`) method names against the `@AgenticAiRecord` output; adjust if the generator names differ. `switch` with a `when` guard requires the Java 21 pattern switch (module is on 21).

- [ ] **Step 5 — Run to pass.** Same command as Step 3 → PASS.

---

## Task 4 — Integrate into `proceed(...)` + wire beans

**Files:** modify `BaseAgentRequestHandler.java`, `JobWorkerAgentRequestHandler.java`, `OutboundConnectorAgentRequestHandler.java`, `AgenticAiConnectorsAutoConfiguration.java`; update `OutboundConnectorAgentRequestHandlerTest.java`, `JobWorkerAgentRequestHandlerTest.java`.

- [ ] **Step 1 — Failing handler test.** In `OutboundConnectorAgentRequestHandlerTest` (mirror in `JobWorkerAgentRequestHandlerTest`): add a `ToolCallResultStrategy` to the handler under test (use the real `CapabilityAwareToolCallResultStrategy` for pre-existing tests — identity when there are no docs). Make the mocked `chatModel.capabilities()` return the bridge profile. Add a test: input tool result carrying a `DocumentContent`; capture the `ChatModelRequest` at `chatModel.call(...)` (ArgumentCaptor) and assert its `snapshot()` has the `DocumentContent` stripped from the tool-result block **and** a trailing `METADATA_TOOL_CALL_DOCUMENTS` synthetic `UserMessage`. Also assert `session.storeMessages(...)` persists the **self-describing** message (DocumentContent still inside the `ToolCallResultMessage`; no synthetic message persisted).

- [ ] **Step 2 — Run to confirm failure.**
`mvn -q -Dmaven.build.cache.enabled=false -f connectors/agentic-ai/pom.xml test -Dtest=OutboundConnectorAgentRequestHandlerTest` → FAIL (ctor arity / strategy not applied).

- [ ] **Step 3 — Enrich `BaseAgentRequestHandler`.** Add `private final ToolCallResultStrategy toolCallResultStrategy;` + ctor param (last position); thread it through both subclass ctors. In `proceed(...)`, inside the `do/while` loop, replace the direct window→call:

```java
final var windowedSnapshot =
    workingConversation.window(agentConfiguration.contextWindowSize());
final var sentSnapshot =
    toolCallResultStrategy.apply(windowedSnapshot, chatModel.capabilities());
final var chatResult = chatModel.call(new ChatModelRequest(executionContext, sentSnapshot));
```

`chatModel` stays resolved once before the loop. Nothing else changes: `session.storeMessages(updatedConversation.toAgentContext(), ConversationStoreRequest.of(updatedConversation.allMessages()))` persists the self-describing messages; the strategy's synthetic message is sent-only and never enters `allMessages()`.

- [ ] **Step 4 — Wire beans.** In `AgenticAiConnectorsAutoConfiguration`:

```java
@Bean
@ConditionalOnMissingBean
public ToolCallResultStrategy aiAgentToolCallResultStrategy() {
  return new CapabilityAwareToolCallResultStrategy();
}
```
Add `ToolCallResultStrategy toolCallResultStrategy` to the parameter lists of `aiAgentOutboundConnectorAgentRequestHandler(...)` and `aiAgentJobWorkerAgentRequestHandler(...)` and pass it to the handler ctors.

- [ ] **Step 5 — Run to pass.**
`mvn -q -Dmaven.build.cache.enabled=false -f connectors/agentic-ai/pom.xml test -Dtest=OutboundConnectorAgentRequestHandlerTest,JobWorkerAgentRequestHandlerTest,AgentConversationTurnInputComposerImplTest,CapabilityAwareToolCallResultStrategyTest,DocumentModalityTest` → PASS.

---

## Task 5 — Window × strategy interaction (multi-turn) unit test

**Files:** create `ToolResultDocumentWindowingTest.java`.

Assert the "Memory window / eviction" contract using the **real** `MessageWindowFilter.apply(...)` on a multi-turn flat message list (each turn = `UserMessage`/`AssistantMessage(hasToolCalls)` + self-describing `ToolCallResultMessage` carrying a `DocumentContent`), then run the strategy on the window with bridge caps.

- [ ] **Step 1 — Write the test.**
  - **In-window:** window size large enough to keep both turns → after strategy, both turns' documents render (each as a `METADATA_TOOL_CALL_DOCUMENTS` synthetic `UserMessage` following its stripped `ToolCallResultMessage`).
  - **Evicted:** window size that evicts turn 1 → turn 1's `AssistantMessage` + `ToolCallResultMessage` are gone **atomically** (no orphan tool-result), only turn 2's document renders; assert the effective window count equals the count for the same messages without any `DocumentContent` (a `ToolCallResultMessage` = 1 slot regardless of carried documents).
  - Build via `MessageWindowFilter.apply(messages, size)`, wrap in a `ConversationSnapshot`, then `strategy.apply(...)`. Use `TestMessagesFixture` helpers where convenient.

- [ ] **Step 2 — Run to pass.**
`mvn -q -Dmaven.build.cache.enabled=false -f connectors/agentic-ai/pom.xml test -Dtest=ToolResultDocumentWindowingTest` → PASS.

---

## Task 6 — Module build + e2e byte-identical guard + commit

- [ ] **Step 1 — Full module build (NullAway/ErrorProne + template regen).**
`mvn -q -Dmaven.build.cache.enabled=false -f connectors/agentic-ai/pom.xml clean install`
Expected: BUILD SUCCESS, **no element-template JSON diff**. Handle any NullAway finding (never suppress). Run `mvn spotless:apply` + `mvn license:format` if the pre-commit hooks flag formatting.

- [ ] **Step 2 — e2e byte-identical assertion (bridge fallback == today).**
First update `ToolCallResultDocumentAssertions.EXTRACTED_DOCUMENTS_PREAMBLE` to `ToolCallResultStrategy.TOOL_CALL_DOCUMENTS_PREAMBLE` (single shared constant). Then:
`mvn -q -Dmaven.build.cache.enabled=false -am -pl connectors-e2e-test/connectors-e2e-test-agentic-ai test -Dtest=AiAgentConnectorToolCallingTests`
Expected: PASS — `ToolCallResultDocumentAssertions.assertExtractedDocumentsUserMessage(...)` asserts the exact `preamble + (<doc/> tag, content-block)*` wire shape. Because bridge `toolResult:[TEXT]`, every tool-result document takes the fallback → byte-identical. Also run any MCP/A2A document tool-calling e2e in the module (search the e2e sources for document tool tests) to confirm gateway extraction parity through the composer lift.
**Edge to verify here:** a tool result whose raw content is a **bare `Document`** (no wrapping object) has content `[DocumentContent]`; the strip leaves it empty → the bridge renders `CONTENT_NO_RESULT` for that block (the reference still reaches the model via the `<doc/>` synthetic message). If any e2e exercises a bare-document-as-direct-tool-result and asserts the reference *in the tool-result text*, escalate (see residual note) rather than weakening the assertion.

- [ ] **Step 3 — Commit (single coherent C5 commit).**

```bash
git add -A
git commit -m "feat(agentic-ai): capability-keyed tool-call-result routing strategy

Make tool-call results self-describing: the turn-input composer lifts
gateway-extracted documents into DocumentContent inside each
ToolCallResultContent (deduped by reference), replacing the eager synthetic
document-message extraction.

Add ToolCallResultStrategy, a sent-only transform run in BaseAgentRequestHandler
between chat-model resolution and the model call on the windowed snapshot: it
keeps tool-result documents inline when the resolved toolResult modality
supports them, otherwise strips them and inserts the byte-identical <doc/>
synthetic user message. User/event-message documents fail loud on unsupported
modalities. The LangChain4j bridge (toolResult:[text]) always takes the
fallback, so behavior is byte-identical for existing variants."
```
(Do not push. Milestone check-in with the user after this chunk.)

---

## Notes carried into later chunks
- C7 (native Anthropic) / C8/C9 (native OpenAI) consume the strategy's transformed snapshot: when their resolved `toolResult` caps allow the modality, the `DocumentContent` stays inline in `ToolCallResultContent.content` and the native impl must render it as a native tool-result content block. A nested document also still appears as a reference inside the sibling `ObjectContent` JSON — de-duplicating that for native rendering is a C7+ concern, not C5.
- `Result` was dropped from the SPI (Option 2 persists nothing); `apply(...)` returns `ConversationSnapshot`. No later chunk referenced `Result`.
