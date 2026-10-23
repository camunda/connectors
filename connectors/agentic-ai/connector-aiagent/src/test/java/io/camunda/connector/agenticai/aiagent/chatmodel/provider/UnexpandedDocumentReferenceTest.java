/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock.BedrockConverseContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini.GeminiContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ContentConverterImpl;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.document.DocumentToContentConverterImpl;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolCallConverterImpl;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.completions.OpenAiCompletionsContentChunkStrategy;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentReferenceContent;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
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
class UnexpandedDocumentReferenceTest {

  private static final ObjectMapper OBJECT_MAPPER = TestObjectMapperSupplier.INSTANCE;

  private static final List<Content> CONTENT =
      List.of(
          new DocumentReferenceContent(
              DocumentId.of("doc-1"),
              "a.txt",
              "text/plain",
              3L,
              DocumentSource.toolCall("tool", "call-1"),
              DocumentDelivery.CONTENT,
              null));

  static Stream<Named<Consumer<List<Content>>>> converters() {
    return Stream.of(
        named(
            "Anthropic user content",
            content -> new AnthropicContentConverter(OBJECT_MAPPER).toContentBlockParams(content)),
        named(
            "Anthropic tool result",
            content -> new AnthropicContentConverter(OBJECT_MAPPER).toToolResultBlocks(content)),
        named(
            "Bedrock user content",
            content -> new BedrockConverseContentConverter(OBJECT_MAPPER).toContentBlocks(content)),
        named(
            "Bedrock tool result",
            content ->
                new BedrockConverseContentConverter(OBJECT_MAPPER).toToolResultBlocks(content)),
        named(
            "Gemini user content",
            content -> new GeminiContentConverter(OBJECT_MAPPER).toParts(content)),
        named(
            "Gemini tool result",
            content -> new GeminiContentConverter(OBJECT_MAPPER).toFunctionResponseParts(content)),
        named(
            "OpenAI Responses user content",
            content -> new OpenAiContentConverter(OBJECT_MAPPER).toResponsesContentParts(content)),
        named(
            "OpenAI Responses tool result",
            content ->
                new OpenAiContentConverter(OBJECT_MAPPER)
                    .toResponsesToolResultOutputItems(content)),
        named(
            "OpenAI Completions user content",
            content ->
                OpenAiCompletionsContentChunkStrategy.openAi(OBJECT_MAPPER)
                    .toContentParts(content)),
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
        .hasMessageContaining("DocumentReferenceContent")
        .hasMessageContaining("expanded");
  }
}
