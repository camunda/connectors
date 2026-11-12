/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ContentConverterImpl;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.document.DocumentToContentConverterImpl;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolCallConverterImpl;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A document reference is expanded to its tag text and document content before a request is built,
 * so no provider converter accepts it.
 */
class UnexpandedAgentDocumentContentTest {

  private static final ObjectMapper OBJECT_MAPPER = TestObjectMapperSupplier.INSTANCE;

  private static final List<Content> CONTENT =
      List.of(
          new AgentDocumentContent(AgentDocumentId.of("doc-1"), DocumentDelivery.CONTENT, null));

  static Stream<Named<Consumer<List<Content>>>> converters() {
    return Stream.of(
        named(
            "LangChain4j user content",
            content -> {
              try {
                new ContentConverterImpl(OBJECT_MAPPER, new DocumentToContentConverterImpl())
                    .convertToContent(content.getFirst());
              } catch (RuntimeException e) {
                throw e;
              } catch (Exception e) {
                throw new IllegalArgumentException(e);
              }
            }),
        named(
            "LangChain4j tool result",
            content ->
                new ToolCallConverterImpl(OBJECT_MAPPER)
                    .asToolExecutionResultMessage(
                        ToolCallResultContent.builder()
                            .id("call-1")
                            .name("tool")
                            .content(content)
                            .build())));
  }

  private static Named<Consumer<List<Content>>> named(String name, Consumer<List<Content>> call) {
    return Named.of(name, call);
  }

  @ParameterizedTest
  @MethodSource("converters")
  void failsLoudlyForADocumentReference(Consumer<List<Content>> converter) {
    assertThatThrownBy(() -> converter.accept(CONTENT))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AgentDocumentContent")
        .hasMessageContaining("expanded");
  }
}
