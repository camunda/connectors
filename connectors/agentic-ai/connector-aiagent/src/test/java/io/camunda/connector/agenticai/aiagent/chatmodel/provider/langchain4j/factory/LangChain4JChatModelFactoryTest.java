/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.systemMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.api.conversation.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRegistryImpl;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ChatMessageConverterImpl;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.CloseableChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ContentConverterImpl;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ToolCallMetadataDecorator;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.document.DocumentToContentConverterImpl;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolCallConverterImpl;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolSpecificationConverterImpl;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.TextResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.request.AgentTaskResponseConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * A pro-code user backs a chat model with LangChain4j by extending {@link
 * LangChain4JChatModelFactory} and registering the subclass as a bean. This test runs such a custom
 * factory through the registry, the real converters and the bridge chat model.
 */
class LangChain4JChatModelFactoryTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final CloseableChatModel l4jModel = mock(CloseableChatModel.class);
  private final CustomFactory factory =
      new CustomFactory(l4jModel, new ToolCallMetadataDecorator() {});
  private final ChatModelRegistryImpl registry =
      new ChatModelRegistryImpl(List.<ChatModelFactory>of(factory));

  @Test
  void runsACustomLangChain4JBackedModelThroughTheRegistryAndTheBridge() {
    final var toolCall =
        ToolExecutionRequest.builder()
            .id("call-1")
            .name("GetTime")
            .arguments("{\"zone\":\"UTC\"}")
            .build();
    final var response =
        ChatResponse.builder()
            .aiMessage(
                AiMessage.builder()
                    .text("It is noon.")
                    .toolExecutionRequests(List.of(toolCall))
                    .build())
            .tokenUsage(new TokenUsage(7, 3))
            .build();
    when(l4jModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class)))
        .thenReturn(response);

    final var chatModel = registry.resolve(new CustomConfiguration("my-model"));
    final var result =
        chatModel.execute(
            new ChatRequest(
                executionContext(),
                new ConversationSnapshot(
                    List.of(systemMessage("Be brief."), userMessage("What time is it?")),
                    List.of(
                        ToolDefinition.builder()
                            .name("GetTime")
                            .description("Returns the time")
                            .inputSchema(Map.of("type", "object"))
                            .build()))));

    final var captor =
        ArgumentCaptor.forClass(dev.langchain4j.model.chat.request.ChatRequest.class);
    verify(l4jModel).chat(captor.capture());
    assertThat(captor.getValue().messages())
        .containsExactly(SystemMessage.from("Be brief."), UserMessage.from("What time is it?"));
    assertThat(captor.getValue().toolSpecifications())
        .singleElement()
        .satisfies(spec -> assertThat(spec.name()).isEqualTo("GetTime"));

    assertThat(result).isInstanceOf(ChatResult.Completed.class);
    assertThat(result.assistantMessage().content())
        .containsExactly(TextContent.textContent("It is noon."));
    assertThat(result.assistantMessage().toolCalls())
        .singleElement()
        .satisfies(
            call -> {
              assertThat(call.id()).isEqualTo("call-1");
              assertThat(call.name()).isEqualTo("GetTime");
              assertThat(call.arguments()).isEqualTo(Map.of("zone", "UTC"));
            });
    assertThat(result.metrics().modelCalls()).isEqualTo(1);
    assertThat(result.metrics().tokenUsage().inputTokenCount()).isEqualTo(7);
    assertThat(result.metrics().tokenUsage().outputTokenCount()).isEqualTo(3);

    chatModel.close();
    verify(l4jModel).close();
  }

  @Test
  void carriesProviderDataThroughToolCallMetadataWithARegisteredDecorator() {
    final var decorator =
        new ToolCallMetadataDecorator() {
          @Override
          public Map<String, Object> decorateOnWrite(
              ChatModelConfiguration configuration,
              String toolCallId,
              Map<String, Object> aiMessageAttributes) {
            return aiMessageAttributes.get("signature_" + toolCallId) instanceof String signature
                ? Map.of("signature", signature)
                : Map.of();
          }

          @Override
          public Map<String, Object> decorateOnRead(
              ChatModelConfiguration configuration, String toolCallId, Map<?, ?> toolCallMetadata) {
            return toolCallMetadata.get("signature") instanceof String signature
                ? Map.of("signature_" + toolCallId, signature)
                : Map.of();
          }
        };
    final var chatModel =
        new CustomFactory(l4jModel, decorator).create(new CustomConfiguration("my-model"));

    final var toolCall =
        ToolExecutionRequest.builder().id("call-1").name("GetTime").arguments("{}").build();
    when(l4jModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class)))
        .thenReturn(
            ChatResponse.builder()
                .aiMessage(
                    AiMessage.builder()
                        .toolExecutionRequests(List.of(toolCall))
                        .attributes(Map.of("signature_call-1", "abc"))
                        .build())
                .build());

    final var first =
        chatModel.execute(
            new ChatRequest(
                executionContext(),
                new ConversationSnapshot(List.of(userMessage("What time is it?")), List.of())));

    assertThat(first.assistantMessage().toolCalls())
        .singleElement()
        .satisfies(call -> assertThat(call.metadata()).isEqualTo(Map.of("signature", "abc")));

    chatModel.execute(
        new ChatRequest(
            executionContext(),
            new ConversationSnapshot(
                List.of(userMessage("What time is it?"), first.assistantMessage()), List.of())));

    final var captor =
        ArgumentCaptor.forClass(dev.langchain4j.model.chat.request.ChatRequest.class);
    verify(l4jModel, times(2)).chat(captor.capture());
    assertThat(captor.getAllValues().get(1).messages())
        .anySatisfy(
            message ->
                assertThat(message)
                    .isInstanceOfSatisfying(
                        AiMessage.class,
                        aiMessage ->
                            assertThat(aiMessage.attributes())
                                .isEqualTo(Map.of("signature_call-1", "abc"))));
  }

  @Test
  void supportsOnlyTheConfigurationTypeOfTheFactory() {
    assertThat(factory.supports(new CustomConfiguration("my-model"))).isTrue();

    final var other = mock(ChatModelConfiguration.class);
    assertThat(factory.supports(other)).isFalse();
    assertThatThrownBy(() -> registry.resolve(other))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No chat model registered");
  }

  private static AgentExecutionContext executionContext() {
    final var executionContext = mock(AgentExecutionContext.class);
    when(executionContext.configuration())
        .thenReturn(
            new AgentConfiguration(
                null,
                null,
                null,
                null,
                null,
                null,
                new AgentTaskResponseConfiguration(
                    new TextResponseFormatConfiguration(false), false)));
    return executionContext;
  }

  private record CustomConfiguration(String model) implements ChatModelConfiguration {
    @Override
    public String provider() {
      return "custom-langchain4j";
    }
  }

  private static class CustomFactory extends LangChain4JChatModelFactory<CustomConfiguration> {

    private final CloseableChatModel model;

    CustomFactory(CloseableChatModel model, ToolCallMetadataDecorator decorator) {
      super(
          CustomConfiguration.class,
          new ChatMessageConverterImpl(
              new ContentConverterImpl(OBJECT_MAPPER, new DocumentToContentConverterImpl()),
              new ToolCallConverterImpl(OBJECT_MAPPER),
              OBJECT_MAPPER,
              decorator),
          new ToolSpecificationConverterImpl(new JsonSchemaConverter(OBJECT_MAPPER)),
          new JsonSchemaConverter(OBJECT_MAPPER));
      this.model = model;
    }

    @Override
    public CloseableChatModel createChatModel(CustomConfiguration configuration) {
      return model;
    }
  }
}
