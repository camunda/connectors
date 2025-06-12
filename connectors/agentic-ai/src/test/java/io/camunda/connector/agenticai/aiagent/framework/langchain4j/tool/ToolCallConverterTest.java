/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.document.DocumentToContentConverterImpl;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.document.Document;
import io.camunda.document.factory.DocumentFactory;
import io.camunda.document.factory.DocumentFactoryImpl;
import io.camunda.document.store.DocumentCreationRequest;
import io.camunda.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

class ToolCallConverterTest {

  private final ToolCallConverter toolCallConverter =
      new ToolCallConverterImpl(new ObjectMapper(), new DocumentToContentConverterImpl());

  @Test
  void convertsToolCallToToolExecutionRequest() throws JSONException {
    final ToolCall toolCall =
        ToolCall.builder()
            .id("123456")
            .name("toolName")
            .arguments(Map.of("key1", "value1", "key2", 42))
            .build();

    final var request = toolCallConverter.asToolExecutionRequest(toolCall);
    assertThat(request.id()).isEqualTo("123456");
    assertThat(request.name()).isEqualTo("toolName");
    JSONAssert.assertEquals("{\"key1\":\"value1\",\"key2\":42}", request.arguments(), true);
  }

  @Test
  void throwsExceptionWhenToolCallArgumentsCannotBeSerialized() {
    final ToolCall toolCall =
        ToolCall.builder()
            .id("123456")
            .name("toolName")
            .arguments(Map.of("dummy", new DummyClass()))
            .build();

    assertThatThrownBy(() -> toolCallConverter.asToolExecutionRequest(toolCall))
        .isInstanceOf(ConnectorException.class)
        .hasMessageStartingWith("Failed to serialize tool call results for tool 'toolName'");
  }

  @Test
  void convertsToolExecutionRequestToToolCall() {
    final var toolExecutionRequest =
        ToolExecutionRequest.builder()
            .id("123456")
            .name("toolName")
            .arguments("{\"key1\":\"value1\",\"key2\":42}")
            .build();

    final ToolCall toolCall = toolCallConverter.asToolCall(toolExecutionRequest);
    assertThat(toolCall.id()).isEqualTo("123456");
    assertThat(toolCall.name()).isEqualTo("toolName");
    assertThat(toolCall.arguments()).isEqualTo(Map.of("key1", "value1", "key2", 42));
  }

  @Test
  void throwsExceptionWhenToolExecutionRequestArgumentsCannotBeDeserialized() {
    final var toolExecutionRequest =
        ToolExecutionRequest.builder().id("123456").name("toolName").arguments("{{}").build();

    assertThatThrownBy(() -> toolCallConverter.asToolCall(toolExecutionRequest))
        .isInstanceOf(ConnectorException.class)
        .hasMessageStartingWith("Failed to deserialize tool call results for tool 'toolName'");
  }

  @Nested
  class AsToolExecutionResultMessage {

    private final InMemoryDocumentStore documentStore = InMemoryDocumentStore.INSTANCE;
    private final DocumentFactory documentFactory = new DocumentFactoryImpl(documentStore);

    @BeforeEach
    void setUp() {
      documentStore.clear();
    }

    @Test
    void supportsStringContentResults() {
      final ToolCallResult toolCallResult =
          ToolCallResult.builder().id("toolId").name("toolName").content("result").build();

      final var resultMessage = toolCallConverter.asToolExecutionResultMessage(toolCallResult);

      assertThat(resultMessage)
          .extracting(
              ToolExecutionResultMessage::id,
              ToolExecutionResultMessage::toolName,
              ToolExecutionResultMessage::text)
          .containsExactly("toolId", "toolName", "result");
    }

    @Test
    void supportsObjectContentResults() {
      final Map<String, Object> content = new LinkedHashMap<>();
      content.put("foo", "bar");
      content.put("list", List.of("A", "B", "C"));

      final ToolCallResult toolCallResult =
          ToolCallResult.builder().id("toolId").name("toolName").content(content).build();

      final var resultMessage = toolCallConverter.asToolExecutionResultMessage(toolCallResult);

      assertThat(resultMessage)
          .extracting(
              ToolExecutionResultMessage::id,
              ToolExecutionResultMessage::toolName,
              ToolExecutionResultMessage::text)
          .containsExactly("toolId", "toolName", "{\"foo\":\"bar\",\"list\":[\"A\",\"B\",\"C\"]}");
    }

    @Test
    void supportsResultsContainingCamundaDocuments() {
      final var content = new LinkedHashMap<String, Object>();
      content.put("hello", "world");
      content.put("document1", createDocument("Hello, world!", "text/plain", "test.txt"));
      content.put("document2", createDocument("<PDF CONTENT>", "application/pdf", "test.pdf"));

      final ToolCallResult toolCallResult =
          ToolCallResult.builder().id("toolId").name("toolName").content(content).build();

      final var resultMessage = toolCallConverter.asToolExecutionResultMessage(toolCallResult);

      assertThat(resultMessage)
          .extracting(
              ToolExecutionResultMessage::id,
              ToolExecutionResultMessage::toolName,
              ToolExecutionResultMessage::text)
          .containsExactly(
              "toolId",
              "toolName",
              "{\"hello\":\"world\",\"document1\":{\"type\":\"text\",\"media_type\":\"text/plain\",\"data\":\"Hello, world!\"},\"document2\":{\"type\":\"base64\",\"media_type\":\"application/pdf\",\"data\":\"PFBERiBDT05URU5UPg==\"}}");
    }

    @Test
    void defaultsIdAndToolNameToEmptyIfMissing() {
      final ToolCallResult toolCallResult = ToolCallResult.builder().content("result").build();

      final var resultMessage = toolCallConverter.asToolExecutionResultMessage(toolCallResult);

      assertThat(resultMessage)
          .extracting(
              ToolExecutionResultMessage::id,
              ToolExecutionResultMessage::toolName,
              ToolExecutionResultMessage::text)
          .containsExactly("", "", "result");
    }

    @Test
    void defaultsIdAndToolNameToEmptyIfNull() {
      final ToolCallResult toolCallResult =
          ToolCallResult.builder().id(null).name(null).content("result").build();

      final var resultMessage = toolCallConverter.asToolExecutionResultMessage(toolCallResult);

      assertThat(resultMessage)
          .extracting(
              ToolExecutionResultMessage::id,
              ToolExecutionResultMessage::toolName,
              ToolExecutionResultMessage::text)
          .containsExactly("", "", "result");
    }

    @Test
    void throwsExceptionWhenContentCannotBeSerialized() {
      final ToolCallResult toolCallResult =
          ToolCallResult.builder().id("toolId").name("toolName").content(new DummyClass()).build();

      assertThatThrownBy(() -> toolCallConverter.asToolExecutionResultMessage(toolCallResult))
          .isInstanceOf(ConnectorException.class)
          .hasMessageStartingWith(
              "Failed to convert result of tool call 'toolName' to string: No serializer found for class %s and no properties discovered to create BeanSerializer"
                  .formatted(DummyClass.class.getName()));
    }

    private Document createDocument(String content, String contentType, String filename) {
      return documentFactory.create(
          DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
              .contentType(contentType)
              .fileName(filename)
              .build());
    }
  }

  private static class DummyClass {}
}
