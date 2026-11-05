/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent.objectContent;
import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.api.tool.GatewayToolHandler;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ToolCallBatchTest {

  private static final ToolCall FIRST_CALL = new ToolCall("first", "getWeather", Map.of());
  private static final ToolCall SECOND_CALL = new ToolCall("second", "getDateTime", Map.of());
  private static final ToolCall THIRD_CALL = new ToolCall("third", "getTime", Map.of());
  private static final List<ToolCall> CALLS = List.of(FIRST_CALL, SECOND_CALL, THIRD_CALL);

  private final DocumentFactoryImpl documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);

  private GatewayToolHandlerRegistry gatewayToolHandlers;
  private ToolCallResultDocumentExtractor extractor;

  @BeforeEach
  void setUp() {
    gatewayToolHandlers = mock(GatewayToolHandlerRegistry.class);
    when(gatewayToolHandlers.handlerForToolDefinition(any())).thenReturn(Optional.empty());
    extractor = new ToolCallResultDocumentExtractor(gatewayToolHandlers);
  }

  private static ToolCallBatch.Entry entry(ToolCall call, String text) {
    return ToolCallBatch.Entry.from(
        ToolCallResultContent.builder()
            .id(call.id())
            .name(call.name())
            .content(List.of(textContent(text)))
            .build());
  }

  private Document document(String name) {
    return documentFactory.create(
        DocumentCreationRequest.from(name.getBytes(StandardCharsets.UTF_8))
            .contentType("text/plain")
            .fileName(name + ".txt")
            .build());
  }

  @Test
  void aBatchOfCallsHasNoResultAndAllCallsUnanswered() {
    var batch = ToolCallBatch.of(CALLS);

    assertThat(batch.toolCalls()).containsExactlyElementsOf(CALLS);
    assertThat(batch.entriesById()).isEmpty();
    assertThat(batch.unanswered()).containsExactlyElementsOf(CALLS);
  }

  @Test
  void anEmptyBatchHasNothingUnanswered() {
    var batch = ToolCallBatch.of(List.of());

    assertThat(batch.unanswered()).isEmpty();
    assertThat(batch.entriesInCallOrder()).isEmpty();
  }

  @Test
  void withEntries_answersTheCallsAndKeepsTheOthersUnansweredInCallOrder() {
    var secondEntry = entry(SECOND_CALL, "second result");

    var batch = ToolCallBatch.of(CALLS).withEntries(List.of(secondEntry));

    assertThat(batch.toolCalls()).containsExactlyElementsOf(CALLS);
    assertThat(batch.entriesById()).containsOnly(Map.entry("second", secondEntry));
    assertThat(batch.unanswered()).containsExactly(FIRST_CALL, THIRD_CALL);
  }

  @Test
  void withEntries_doesNotChangeTheBatchItIsCalledOn() {
    var batch = ToolCallBatch.of(CALLS);

    batch.withEntries(List.of(entry(FIRST_CALL, "first result")));

    assertThat(batch).isEqualTo(ToolCallBatch.of(CALLS));
  }

  @Test
  void withEntries_addsToTheEntriesOfEarlierCalls() {
    var firstEntry = entry(FIRST_CALL, "first result");
    var thirdEntry = entry(THIRD_CALL, "third result");

    var batch =
        ToolCallBatch.of(CALLS).withEntries(List.of(thirdEntry)).withEntries(List.of(firstEntry));

    assertThat(batch.entriesById())
        .containsOnly(Map.entry("first", firstEntry), Map.entry("third", thirdEntry));
    assertThat(batch.unanswered()).containsExactly(SECOND_CALL);
  }

  @Test
  void entriesInCallOrder_followsTheCallsNotTheOrderTheResultsWereAdded() {
    var firstEntry = entry(FIRST_CALL, "first result");
    var secondEntry = entry(SECOND_CALL, "second result");
    var thirdEntry = entry(THIRD_CALL, "third result");

    var batch =
        ToolCallBatch.of(CALLS)
            .withEntries(List.of(thirdEntry, firstEntry))
            .withEntries(List.of(secondEntry));

    assertThat(batch.unanswered()).isEmpty();
    assertThat(batch.entriesInCallOrder()).containsExactly(firstEntry, secondEntry, thirdEntry);
  }

  @Test
  void entriesInCallOrder_failsWhileACallHasNoResult() {
    var batch = ToolCallBatch.of(CALLS).withEntries(List.of(entry(SECOND_CALL, "second result")));

    assertThatThrownBy(batch::entriesInCallOrder)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("first")
        .hasMessageContaining("third")
        .hasMessageNotContaining("second");
  }

  @Test
  void withEntries_failsForASecondResultOfACall() {
    var batch = ToolCallBatch.of(CALLS).withEntries(List.of(entry(FIRST_CALL, "first result")));
    var again = List.of(entry(FIRST_CALL, "another result"));

    assertThatThrownBy(() -> batch.withEntries(again))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("first");
  }

  @Test
  void withEntries_failsForTwoResultsOfACallInOneCall() {
    var batch = ToolCallBatch.of(CALLS);
    var twice = List.of(entry(FIRST_CALL, "first result"), entry(FIRST_CALL, "another result"));

    assertThatThrownBy(() -> batch.withEntries(twice))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("first");
  }

  @Test
  void withEntries_failsForAResultOfNoCallOfTheBatch() {
    var batch = ToolCallBatch.of(List.of(FIRST_CALL));
    var stray = List.of(entry(new ToolCall("stray", "other", Map.of()), "stray result"));

    assertThatThrownBy(() -> batch.withEntries(stray))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("stray");
  }

  @Test
  void theConstructorFailsForAnEntryOfNoCallOfTheBatch() {
    var entries = Map.of("stray", entry(new ToolCall("stray", "other", Map.of()), "result"));

    assertThatThrownBy(() -> new ToolCallBatch(List.of(FIRST_CALL), entries))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("stray");
  }

  @Test
  void entryFromStructuredContent_takesTheDocumentsOfTheContent() {
    var directDocument = document("direct");
    var nestedDocument = document("nested");
    var content =
        ToolCallResultContent.builder()
            .id("first")
            .name("getWeather")
            .content(
                List.of(
                    textContent("text"),
                    DocumentContent.documentContent(directDocument),
                    objectContent(Map.of("file", nestedDocument))))
            .build();

    var entry = ToolCallBatch.Entry.from(content);

    assertThat(entry.result()).isEqualTo(content);
    assertThat(entry.documents()).containsExactly(directDocument, nestedDocument);
  }

  @Test
  void entryFromStructuredContent_withoutDocumentsHasNone() {
    var content =
        ToolCallResultContent.builder()
            .id("first")
            .name("getWeather")
            .content(List.of(textContent("text")))
            .build();

    var entry = ToolCallBatch.Entry.from(content);

    assertThat(entry.result()).isEqualTo(content);
    assertThat(entry.documents()).isEmpty();
  }

  @Test
  void entryFromAProcessResult_liftsTheResultAndExtractsItsDocuments() {
    var document = document("attachment");
    var result =
        ToolCallResult.builder()
            .id("first")
            .name("getWeather")
            .content(Map.of("result", "Sunny", "attachment", document))
            .build();

    var entry = ToolCallBatch.Entry.from(result, extractor);

    assertThat(entry.result()).isEqualTo(ToolCallResultContent.from(result));
    assertThat(entry.documents()).containsExactly(document);
  }

  @Test
  void entryFromAProcessResult_withoutDocumentsHasNone() {
    var result = ToolCallResult.builder().id("first").name("getWeather").content("Sunny").build();

    var entry = ToolCallBatch.Entry.from(result, extractor);

    assertThat(entry.result()).isEqualTo(ToolCallResultContent.from(result));
    assertThat(entry.documents()).isEmpty();
  }

  @Test
  void entryFromAProcessResult_ofAGatewayToolTakesTheDocumentsOfItsHandler() {
    var document = document("gateway");
    var result =
        ToolCallResult.builder().id("first").name("MCP_server").content("gateway content").build();
    var handler = mock(GatewayToolHandler.class);
    when(handler.extractDocuments(result)).thenReturn(List.of(document));
    when(gatewayToolHandlers.handlerForToolDefinition("MCP_server"))
        .thenReturn(Optional.of(handler));

    var entry = ToolCallBatch.Entry.from(result, extractor);

    assertThat(entry.result()).isEqualTo(ToolCallResultContent.from(result));
    assertThat(entry.documents()).containsExactly(document);
    verify(handler).extractDocuments(result);
    verifyNoMoreInteractions(handler);
  }

  @Test
  void anEntryNeedsAResultWithAnId() {
    var withoutId = ToolCallResultContent.builder().name("getWeather").build();

    assertThatThrownBy(() -> ToolCallBatch.Entry.from(withoutId))
        .isInstanceOf(NullPointerException.class);
  }
}
