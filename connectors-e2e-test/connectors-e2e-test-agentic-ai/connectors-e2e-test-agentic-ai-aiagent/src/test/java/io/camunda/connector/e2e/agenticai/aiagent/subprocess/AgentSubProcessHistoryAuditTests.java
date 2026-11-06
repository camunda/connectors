/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.e2e.agenticai.aiagent.subprocess;

import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.FINAL_TEXT;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.downloadTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.finalTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.getDocumentTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.returnDocumentWithPingTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.stubUploadEndpoint;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.uploadTurn;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.uploadUrl;
import static io.camunda.connector.e2e.agenticai.aiagent.wiremock.DocumentIdPlaceholderTransformer.documentId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.api.command.AgentInstanceHistoryContent;
import io.camunda.client.api.search.enums.AgentInstanceHistoryCommitStatus;
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.client.api.search.response.AgentInstanceHistory;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandle;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.agenticai.aiagent.common.AgentCoreMemoryTestConfiguration;
import io.camunda.connector.e2e.agenticai.aiagent.common.InMemoryBedrockAgentCoreClientFactory;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation.RecordedMessage;
import io.camunda.connector.runtime.core.document.CamundaDocumentReferenceImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;

/**
 * Guards that the agent instance history is an auditable record of what the model receives: every
 * message of every model request must be covered by an item of the history as the engine returns
 * it. The formats may differ (a document is a block in the history and a handle in the request),
 * the content may not.
 */
@SlowTest
@Import(AgentCoreMemoryTestConfiguration.class)
public class AgentSubProcessHistoryAuditTests extends BaseAgentSubProcessTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Value("classpath:agentic-ai-ahsp-connectors-documents.bpmn")
  private Resource documentsProcess;

  @Value("classpath:agentic-ai-ahsp-connectors-documents-content.bpmn")
  private Resource contentDocumentsProcess;

  @Value("classpath:agentic-ai-ahsp-connectors-documents-results.bpmn")
  private Resource resultDocumentsProcess;

  @Override
  protected Resource testProcess() {
    return documentsProcess;
  }

  @BeforeEach
  void setUpDocumentFlow() {
    InMemoryBedrockAgentCoreClientFactory.INSTANCE.clear();
    stubUploadEndpoint();
  }

  /**
   * The user prompt carries a document, a tool returns one, the model passes both to a tool and
   * returns one next to a real call.
   */
  @Test
  void everyModelVisibleMessageOfADocumentFlowIsInTheHistory() throws Exception {
    stubDocumentFlow();

    final var agentInstanceKey = runAndGetAgentInstanceKey(e -> e);

    assertHistoryCoversEveryModelRequest(agentInstanceKey);
  }

  /**
   * Documents whose content the agent adds without being asked: the user prompt document through
   * the document delivery of the prompt, the documents of a tool with the delivery {@code content},
   * and the note of a document that fails the content guard. The content shows in the history next
   * to its handle, the note in the handle.
   */
  @Test
  void everyModelVisibleMessageOfADocumentWithTheDeliveryContentIsInTheHistory() throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        downloadTurn(wireMock.getHttpBaseUrl(), "test.pdf"),
        downloadTurn(wireMock.getHttpBaseUrl(), "unsupported.zip", "call_download_zip"),
        finalTurn());

    final var agentInstanceKey =
        runAndGetAgentInstanceKey(
            contentDocumentsProcess,
            e -> e.property("data.userPrompt.documentDelivery", "content"));

    final var messages = OpenAiCompletionsRecordedConversation.recorded().lastRequest().messages();
    assertThat(messages.toString())
        .contains("Pass the handle to a tool that processes it.")
        .doesNotContain("c8__load_document");
    assertHistoryCoversEveryModelRequest(agentInstanceKey);
  }

  /**
   * Two tools return the attached document: one as the whole result, one nested in an object (and
   * twice in it). The history shows the document of the first result as its handle and the document
   * and the second result as the object with the handles nested in it followed by the document
   * once.
   */
  @Test
  void everyModelVisibleMessageOfToolResultDocumentsIsInTheHistory() throws Exception {
    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.toolCalls(
            "I will fetch the file and describe it.",
            10,
            20,
            ToolCall.of("call_fetch", "Fetch_A_File", "{}"),
            ToolCall.of("call_describe", "Describe_A_File", "{}")),
        finalTurn());

    final var agentInstanceKey = runAndGetAgentInstanceKey(resultDocumentsProcess, e -> e);

    assertHistoryCoversEveryModelRequest(agentInstanceKey);

    final var history = awaitCommittedHistory(agentInstanceKey);
    final var fetched = toolResultOf(history, "call_fetch");
    final var described = toolResultOf(history, "call_describe");
    final var promptDocumentHandle = promptDocumentHandleInRequests();
    assertThat(fetched.getContent()).hasSize(2);
    assertThat(fetched.getContent().get(0))
        .isInstanceOfSatisfying(
            AgentInstanceHistoryContent.ObjectContent.class,
            object -> assertThat(object.getObject()).isEqualTo(promptDocumentHandle));
    assertThat(fetched.getContent().get(1))
        .isInstanceOf(AgentInstanceHistoryContent.DocumentContent.class);

    assertThat(described.getContent()).hasSize(2);
    assertThat(described.getContent().get(0))
        .isInstanceOfSatisfying(
            AgentInstanceHistoryContent.ObjectContent.class,
            object ->
                assertThat(object.getObject())
                    .isEqualTo(
                        Map.of(
                            "status",
                            "found",
                            "file",
                            promptDocumentHandle,
                            "related",
                            List.of(promptDocumentHandle))));
    assertThat(described.getContent().get(1))
        .isInstanceOf(AgentInstanceHistoryContent.DocumentContent.class);
  }

  /** With a window that evicts the download, every message of every request is still covered. */
  private void stubDocumentFlow() {
    OpenAiCompletionsChatModelStubs.stubConversation(
        downloadTurn(wireMock.getHttpBaseUrl()),
        // the documents of the request in order of appearance: the prompt document, the download;
        // the prompt document is loaded into the conversation, the download is only referenced
        getDocumentTurn(documentId(0)),
        uploadTurn(documentId(1)),
        returnDocumentWithPingTurn(),
        finalTurn());
  }

  private long runAndGetAgentInstanceKey(Function<ElementTemplate, ElementTemplate> modifier)
      throws Exception {
    return runAndGetAgentInstanceKey(documentsProcess, modifier);
  }

  private long runAndGetAgentInstanceKey(
      Resource process, Function<ElementTemplate, ElementTemplate> modifier) throws Exception {
    final var promptDocument =
        InMemoryDocumentStore.INSTANCE.createDocument(
            DocumentCreationRequest.from("the prompt document".getBytes())
                .fileName("prompt.txt")
                .contentType("text/plain")
                .build());
    final Map<String, Object> reference =
        Map.of(
            "camunda.document.type", "camunda",
            "storeId", promptDocument.getStoreId(),
            "documentId", promptDocument.getDocumentId(),
            "contentHash", promptDocument.getContentHash(),
            "metadata", Map.of("fileName", "prompt.txt", "contentType", "text/plain"));

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(
                process,
                modifier,
                Map.of(
                    "userPrompt",
                    "Look at the attached file, download the other one, upload it and give me back.",
                    "downloadedFiles",
                    List.of(reference),
                    "uploadUrl",
                    uploadUrl(wireMock.getHttpBaseUrl()))));

    final var agentInstanceKey = new AtomicLong();
    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            agentInstanceKey.set(agentResponse.context().metadata().agentInstanceKey().value()));
    return agentInstanceKey.get();
  }

  private void assertHistoryCoversEveryModelRequest(long agentInstanceKey) {
    final var history = awaitCommittedHistory(agentInstanceKey);
    final var requests = OpenAiCompletionsRecordedConversation.recorded().requests();
    assertThat(requests).isNotEmpty();

    final var uncovered = new ArrayList<String>();
    for (int i = 0; i < requests.size(); i++) {
      for (final var message : requests.get(i).messages()) {
        if (!isCovered(message, history)) {
          uncovered.add("request %d: %s".formatted(i + 1, describe(message)));
        }
      }
    }
    assertThat(uncovered)
        .as("model-visible messages without a covering agent instance history item")
        .isEmpty();
  }

  private static AgentInstanceHistory toolResultOf(
      List<AgentInstanceHistory> history, String toolCallId) {
    return history.stream()
        .filter(item -> item.getRole() == AgentInstanceHistoryRole.TOOL_RESULT)
        .filter(item -> toolCallId.equals(item.getToolCalls().getFirst().getToolCallId()))
        .findFirst()
        .orElseThrow();
  }

  /** The handle of the prompt document as the model read it in the result of the first tool. */
  private static Map<String, Object> promptDocumentHandleInRequests() {
    final var result =
        OpenAiCompletionsRecordedConversation.recorded().lastRequest().messages().stream()
            .filter(message -> "call_fetch".equals(message.toolCallId()))
            .findFirst()
            .orElseThrow();
    try {
      return OBJECT_MAPPER.readValue(result.content(), new TypeReference<>() {});
    } catch (Exception e) {
      throw new IllegalStateException(
          "The result of the tool is no handle: " + result.content(), e);
    }
  }

  private List<AgentInstanceHistory> awaitCommittedHistory(long agentInstanceKey) {
    return await()
        .alias("committed agent instance history")
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .until(
            () ->
                camundaClient
                    .newAgentInstanceHistorySearchRequest(agentInstanceKey)
                    .filter(f -> f.commitStatus(AgentInstanceHistoryCommitStatus.COMMITTED))
                    .sort(s -> s.historyItemKey().asc())
                    .execute()
                    .items(),
            items ->
                items.stream()
                    .anyMatch(item ->
                        item.getRole() == AgentInstanceHistoryRole.ASSISTANT
                            && item.getContent().stream()
                                .anyMatch(block ->
                                    block instanceof AgentInstanceHistoryContent.TextContent text
                                        && FINAL_TEXT.equals(text.getText()))));
  }

  private static boolean isCovered(RecordedMessage message, List<AgentInstanceHistory> history) {
    return switch (message.role()) {
      case "system" ->
          history.stream()
              .filter(item -> item.getRole() == AgentInstanceHistoryRole.CONFIGURATION)
              .anyMatch(item -> blocksCover(item.getSystemPrompt(), modelParts(message)));
      case "user" ->
          history.stream()
              .filter(item -> item.getRole() == AgentInstanceHistoryRole.USER)
              .anyMatch(item -> blocksCover(item.getContent(), modelParts(message)));
      case "assistant" ->
          history.stream()
              .filter(item -> item.getRole() == AgentInstanceHistoryRole.ASSISTANT)
              .anyMatch(item -> assistantCovers(item, message));
      case "tool" ->
          history.stream()
              .filter(item -> item.getRole() == AgentInstanceHistoryRole.TOOL_RESULT)
              .anyMatch(item -> toolResultCovers(item, message));
      default -> false;
    };
  }

  private static boolean assistantCovers(AgentInstanceHistory item, RecordedMessage message) {
    if (!blocksCover(item.getContent(), modelParts(message))) {
      return false;
    }
    final var calls = item.getToolCalls();
    if (calls.size() != message.toolCalls().size()) {
      return false;
    }
    for (int i = 0; i < calls.size(); i++) {
      final var reported = calls.get(i);
      final var sent = message.toolCalls().get(i);
      if (!reported.getToolCallId().equals(sent.id())
          || !reported.getToolName().equals(sent.name())
          || !argumentsCover(reported.getArguments(), sent.argumentsJson())) {
        return false;
      }
    }
    return true;
  }

  private static boolean toolResultCovers(AgentInstanceHistory item, RecordedMessage message) {
    return item.getToolCalls().size() == 1
        && item.getToolCalls().getFirst().getToolCallId().equals(message.toolCallId())
        && joinedBlocksCover(item.getContent(), 0, message.textContent());
  }

  /**
   * A tool result of the chat completions request is one text: its parts joined by newlines. The
   * blocks of a history item cover it when their texts and objects follow each other in it,
   * separated by one newline, and a document block is the handle of its document in the text or has
   * the handle of its document in an earlier block (the result holds no document content).
   */
  private static boolean joinedBlocksCover(
      List<AgentInstanceHistoryContent> blocks, int blockIndex, String text) {
    if (blockIndex == blocks.size()) {
      return text.isEmpty();
    }
    final var block = blocks.get(blockIndex);
    final String expected;
    if (block instanceof AgentInstanceHistoryContent.DocumentContent document) {
      // compact JSON has no raw newline, so the handle ends at the first one
      final var end = text.indexOf('\n');
      final var candidate = end < 0 ? text : text.substring(0, end);
      if (!isHandleOf(candidate, documentIdOf(document))) {
        return holdsHandleBefore(blocks, blockIndex, document)
            && joinedBlocksCover(blocks, blockIndex + 1, text);
      }
      expected = candidate;
    } else if (block instanceof AgentInstanceHistoryContent.TextContent textBlock) {
      expected = textBlock.getText();
      if (!text.startsWith(expected)) {
        return false;
      }
    } else if (block instanceof AgentInstanceHistoryContent.ObjectContent object) {
      // compact JSON has no raw newline, so the object ends at the first one
      final var end = text.indexOf('\n');
      expected = end < 0 ? text : text.substring(0, end);
      if (!jsonEquals(OBJECT_MAPPER.valueToTree(object.getObject()), expected)) {
        return false;
      }
    } else {
      return false;
    }
    final var remaining = text.substring(expected.length());
    if (remaining.isEmpty()) {
      return joinedBlocksCover(blocks, blockIndex + 1, "");
    }
    return remaining.startsWith("\n")
        && joinedBlocksCover(blocks, blockIndex + 1, remaining.substring(1));
  }

  /** The history holds the arguments of the call as the model sent them. */
  private static boolean argumentsCover(Map<String, Object> reported, String sentJson) {
    try {
      final Map<String, Object> sent = OBJECT_MAPPER.readValue(sentJson, new TypeReference<>() {});
      return reported.equals(sent);
    } catch (Exception e) {
      return false;
    }
  }

  /** A part of a message of the request: its text, or {@code null} for a non-text part. */
  private record ModelPart(String text) {}

  private static List<ModelPart> modelParts(RecordedMessage message) {
    if (message.content() != null) {
      return List.of(new ModelPart(message.content()));
    }
    return message.contentParts().stream()
        .map(part ->
            new ModelPart(
                "text".equals(part.path("type").asText()) ? part.path("text").asText() : null))
        .toList();
  }

  /**
   * The blocks of a history item cover the parts of a request message when they pair up one to one:
   * a text block equals its text part, an object block is the JSON of its part, and a document
   * block is a part that is not a handle (the document content, as text or as a file) or the handle
   * of the document. A document block has no part when an earlier block of the same item holds the
   * handle of the document, as text or nested in an object: the model sees the handle and the
   * history adds the document reference.
   */
  private static boolean blocksCover(
      List<AgentInstanceHistoryContent> blocks, List<ModelPart> parts) {
    return blocksCover(blocks, 0, parts, 0);
  }

  private static boolean blocksCover(
      List<AgentInstanceHistoryContent> blocks,
      int blockIndex,
      List<ModelPart> parts,
      int partIndex) {
    if (blockIndex == blocks.size()) {
      return partIndex == parts.size();
    }
    final var block = blocks.get(blockIndex);
    final var part = partIndex < parts.size() ? parts.get(partIndex) : null;
    if (block instanceof AgentInstanceHistoryContent.DocumentContent document) {
      final var id = documentIdOf(document);
      final var referenced = holdsHandleBefore(blocks, blockIndex, document);
      final var loaded = part != null && (part.text() == null || !isHandle(part.text()));
      final var handled = part != null && part.text() != null && isHandleOf(part.text(), id);
      return ((loaded || handled) && blocksCover(blocks, blockIndex + 1, parts, partIndex + 1))
          || (referenced && blocksCover(blocks, blockIndex + 1, parts, partIndex));
    }
    if (part == null) {
      return false;
    }
    final var covered =
        switch (block) {
          case AgentInstanceHistoryContent.TextContent text ->
              part.text() != null && text.getText().equals(part.text());
          case AgentInstanceHistoryContent.ObjectContent object ->
              part.text() != null
                  && jsonEquals(OBJECT_MAPPER.valueToTree(object.getObject()), part.text());
          default -> false;
        };
    return covered && blocksCover(blocks, blockIndex + 1, parts, partIndex + 1);
  }

  /** The reported JSON equals the sent JSON exactly. */
  private static boolean jsonEquals(JsonNode reported, String sentText) {
    try {
      return reported.equals(OBJECT_MAPPER.readTree(sentText));
    } catch (Exception e) {
      return false;
    }
  }

  /** Whether the text is a document handle. */
  private static boolean isHandle(String text) {
    return text.startsWith("{\"type\":\"" + DocumentHandle.TYPE + "\"");
  }

  /** Whether the text is exactly the handle of the document with the given id. */
  private static boolean isHandleOf(String text, AgentDocumentId id) {
    if (!isHandle(text)) {
      return false;
    }
    try {
      return isHandleNodeOf(OBJECT_MAPPER.readTree(text), id);
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean isHandleNodeOf(JsonNode node, AgentDocumentId id) {
    return node.isObject()
        && DocumentHandle.TYPE.equals(node.path("type").asText())
        && id.value().equals(node.path("id").asText());
  }

  private static AgentDocumentId documentIdOf(
      AgentInstanceHistoryContent.DocumentContent document) {
    final var reference = document.getDocumentReference();
    return AgentDocumentId.of(
        new CamundaDocumentReferenceImpl(
            reference.getStoreId(), reference.getDocumentId(), null, null));
  }

  /** Whether an earlier block of the item holds the handle of the document. */
  private static boolean holdsHandleBefore(
      List<AgentInstanceHistoryContent> blocks,
      int blockIndex,
      AgentInstanceHistoryContent.DocumentContent document) {
    final var id = documentIdOf(document);
    return blocks.subList(0, blockIndex).stream()
        .anyMatch(earlier ->
            switch (earlier) {
              case AgentInstanceHistoryContent.TextContent text -> isHandleOf(text.getText(), id);
              case AgentInstanceHistoryContent.ObjectContent object ->
                  holdsHandle(OBJECT_MAPPER.valueToTree(object.getObject()), id);
              default -> false;
            });
  }

  private static boolean holdsHandle(JsonNode node, AgentDocumentId id) {
    if (isHandleNodeOf(node, id)) {
      return true;
    }
    for (final var child : node) {
      if (holdsHandle(child, id)) {
        return true;
      }
    }
    return false;
  }

  private static String describe(RecordedMessage message) {
    return "role=%s toolCallId=%s toolCalls=%s text=%s"
        .formatted(
            message.role(), message.toolCallId(), message.toolCalls(), message.textContent());
  }
}
