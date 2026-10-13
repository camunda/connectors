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
import io.camunda.connector.agenticai.aiagent.document.EvictedDocumentsNote;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.agenticai.AgentCoreMemoryTestConfiguration;
import io.camunda.connector.e2e.agenticai.InMemoryBedrockAgentCoreClientFactory;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation.RecordedMessage;
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
 * it. The formats may differ (a document is a block in the history and a tag in the request, a
 * history tag text is wrapped in backticks), the content may not.
 *
 * <p>The only model-visible messages without a history item are listed in {@link
 * #KNOWN_UNREPORTED}. A message that is neither covered nor listed fails the test.
 */
@SlowTest
@Import(AgentCoreMemoryTestConfiguration.class)
public class AgentSubProcessHistoryAuditTests extends BaseAgentSubProcessTest {

  /**
   * Model-visible messages the history does not report, identified by the start of their text. The
   * evicted-documents note is built per request from the registry and sent to the model, but is
   * never part of the stored conversation, so no history item exists for it (gap documented in
   * docs/design/ai-agent-document-flow.md, "History auditability").
   */
  private static final List<String> KNOWN_UNREPORTED = List.of(EvictedDocumentsNote.PREAMBLE);

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Value("classpath:agentic-ai-ahsp-connectors-documents.bpmn")
  private Resource documentsProcess;

  @Value("classpath:agentic-ai-ahsp-connectors-documents-content.bpmn")
  private Resource contentDocumentsProcess;

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
   * to its tag, the note as text.
   */
  @Test
  void everyModelVisibleMessageOfContentDocumentDeliveryIsInTheHistory() throws Exception {
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
        .contains("was not loaded automatically")
        .doesNotContain("c8__get_document");
    assertHistoryCoversEveryModelRequest(agentInstanceKey);
  }

  /**
   * With a window that evicts the download, the evicted-documents note is the only message without
   * a history item.
   */
  @Test
  void onlyTheEvictedDocumentsNoteIsWithoutAHistoryItem() throws Exception {
    stubDocumentFlow();

    final var agentInstanceKey =
        runAndGetAgentInstanceKey(e -> e.property("data.memory.contextWindowSize", "=3"));

    final var requests = OpenAiCompletionsRecordedConversation.recorded().requests();
    assertThat(requests)
        .as("a request carries the evicted-documents note")
        .anyMatch(
            request ->
                request.messages().stream()
                    .anyMatch(m -> m.textContent().startsWith(EvictedDocumentsNote.PREAMBLE)));
    assertHistoryCoversEveryModelRequest(agentInstanceKey);
    // the gap this allowlist entry stands for: no history item holds the note
    assertThat(awaitCommittedHistory(agentInstanceKey))
        .noneMatch(
            item ->
                item.getContent().stream()
                    .anyMatch(
                        block ->
                            block instanceof AgentInstanceHistoryContent.TextContent text
                                && text.getText().startsWith(EvictedDocumentsNote.PREAMBLE)));
  }

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
            agentInstanceKey.set(agentResponse.context().metadata().agentInstanceKey()));
    return agentInstanceKey.get();
  }

  private void assertHistoryCoversEveryModelRequest(long agentInstanceKey) {
    final var history = awaitCommittedHistory(agentInstanceKey);
    final var requests = OpenAiCompletionsRecordedConversation.recorded().requests();
    assertThat(requests).isNotEmpty();

    final var uncovered = new ArrayList<String>();
    for (int i = 0; i < requests.size(); i++) {
      for (final var message : requests.get(i).messages()) {
        if (!isKnownUnreported(message) && !isCovered(message, history)) {
          uncovered.add("request %d: %s".formatted(i + 1, describe(message)));
        }
      }
    }
    assertThat(uncovered)
        .as("model-visible messages without a covering agent instance history item")
        .isEmpty();
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
                    .anyMatch(
                        item ->
                            item.getRole() == AgentInstanceHistoryRole.ASSISTANT
                                && item.getContent().stream()
                                    .anyMatch(
                                        block ->
                                            block
                                                    instanceof
                                                    AgentInstanceHistoryContent.TextContent text
                                                && FINAL_TEXT.equals(text.getText()))));
  }

  private static boolean isKnownUnreported(RecordedMessage message) {
    return "user".equals(message.role())
        && KNOWN_UNREPORTED.stream().anyMatch(message.textContent()::startsWith);
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
        && blocksCover(item.getContent(), modelParts(message));
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
        .map(
            part ->
                new ModelPart(
                    "text".equals(part.path("type").asText()) ? part.path("text").asText() : null))
        .toList();
  }

  /**
   * The blocks of a history item cover the parts of a request message when they pair up one to one:
   * a text block equals its text part without the inline code backticks of the history, an object
   * block is the JSON of its part with the document references shown as tags, and a document block
   * is a part that is not a tag (the document content, as text or as a file). A document block that
   * follows a tag text has no part when the document is only referenced: the model sees the tag and
   * the history adds the document reference.
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
    if (block instanceof AgentInstanceHistoryContent.DocumentContent) {
      final var followsTag =
          blockIndex > 0
              && blocks.get(blockIndex - 1) instanceof AgentInstanceHistoryContent.TextContent tag
              && tag.getText().contains("<doc ");
      final var loaded = part != null && (part.text() == null || !part.text().contains("<doc "));
      return (loaded && blocksCover(blocks, blockIndex + 1, parts, partIndex + 1))
          || (followsTag && blocksCover(blocks, blockIndex + 1, parts, partIndex));
    }
    if (part == null) {
      return false;
    }
    final var covered =
        switch (block) {
          case AgentInstanceHistoryContent.TextContent text ->
              part.text() != null && strip(text.getText()).equals(strip(part.text()));
          case AgentInstanceHistoryContent.ObjectContent object ->
              part.text() != null
                  && jsonCovers(OBJECT_MAPPER.valueToTree(object.getObject()), part.text());
          default -> false;
        };
    return covered && blocksCover(blocks, blockIndex + 1, parts, partIndex + 1);
  }

  private static boolean jsonCovers(JsonNode reported, String sentText) {
    try {
      return jsonCovers(reported, OBJECT_MAPPER.readTree(sentText));
    } catch (Exception e) {
      return false;
    }
  }

  /** The reported JSON equals the sent JSON, except that a document reference is a tag there. */
  private static boolean jsonCovers(JsonNode reported, JsonNode sent) {
    if (reported.isObject() && reported.has("camunda.document.type")) {
      return sent.isTextual()
          && sent.asText().contains("<doc id=\"" + reported.path("documentId").asText() + "\"");
    }
    if (reported.isObject()) {
      return sent.isObject()
          && reported.size() == sent.size()
          && reported.properties().stream()
              .allMatch(
                  field ->
                      sent.has(field.getKey())
                          && jsonCovers(field.getValue(), sent.get(field.getKey())));
    }
    if (reported.isArray()) {
      if (!sent.isArray() || reported.size() != sent.size()) {
        return false;
      }
      for (int i = 0; i < reported.size(); i++) {
        if (!jsonCovers(reported.get(i), sent.get(i))) {
          return false;
        }
      }
      return true;
    }
    return reported.equals(sent);
  }

  private static String strip(String text) {
    return text.replace("`", "");
  }

  private static String describe(RecordedMessage message) {
    return "role=%s toolCallId=%s toolCalls=%s text=%s"
        .formatted(
            message.role(), message.toolCallId(), message.toolCalls(), message.textContent());
  }
}
