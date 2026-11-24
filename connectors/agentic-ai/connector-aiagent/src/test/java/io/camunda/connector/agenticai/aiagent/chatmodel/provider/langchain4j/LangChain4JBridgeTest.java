/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.systemMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatRequest;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatResult;
import io.camunda.connector.agenticai.aiagent.api.conversation.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRegistryImpl;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.document.DocumentToContentConverterImpl;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolCallConverterImpl;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolSpecificationConverterImpl;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.CustomChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.TextResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.request.AgentTaskResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.request.v2.CustomProviderConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * A pro-code user backs a chat model with LangChain4j by extending {@link
 * LangChain4JChatModelFactory} and registering the subclass as a bean. This test runs such custom
 * factories through the registry, the real converters and the bridge chat model.
 */
class LangChain4JBridgeTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final String PROVIDER = "custom-langchain4j";

  private final CloseableChatModel l4jModel = mock(CloseableChatModel.class);
  private final CustomFactory factory = new CustomFactory(l4jModel);
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
                            .inputSchema(
                                Map.of(
                                    "type",
                                    "object",
                                    "properties",
                                    Map.of(
                                        "zone",
                                        Map.of("type", "string", "description", "IANA time zone")),
                                    "required",
                                    List.of("zone")))
                            .build()))));

    final var captor =
        ArgumentCaptor.forClass(dev.langchain4j.model.chat.request.ChatRequest.class);
    verify(l4jModel).chat(captor.capture());
    assertThat(captor.getValue().messages())
        .containsExactly(SystemMessage.from("Be brief."), UserMessage.from("What time is it?"));
    assertThat(captor.getValue().toolSpecifications())
        .containsExactly(
            ToolSpecification.builder()
                .name("GetTime")
                .description("Returns the time")
                .parameters(
                    JsonObjectSchema.builder()
                        .addProperty(
                            "zone",
                            JsonStringSchema.builder().description("IANA time zone").build())
                        .required("zone")
                        .build())
                .build());
    assertThat(captor.getValue().responseFormat()).isNull();

    assertThat(result).isInstanceOf(ChatResult.Completed.class);
    assertThat(result.assistantMessage().content())
        .containsExactly(TextContent.textContent("It is noon."));
    assertThat(result.assistantMessage().toolCalls())
        .containsExactly(new ToolCall("call-1", "GetTime", Map.of("zone", "UTC")));
    assertThat(result.metrics())
        .usingRecursiveComparison()
        .ignoringFields("executionTime")
        .isEqualTo(new AgentMetrics(1, new AgentMetrics.TokenUsage(7, 3), 1, null));

    chatModel.close();
    verify(l4jModel).close();
  }

  @Test
  void carriesProviderDataThroughToolCallMetadataWithTheCodec() {
    final var chatModel =
        new SignatureFactory(l4jModel).create(new CustomConfiguration("my-model"));

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
        .containsExactly(
            new ToolCall(
                "call-1", "GetTime", Map.of(), Map.of(PROVIDER, Map.of("signature", "abc"))));

    chatModel.execute(
        new ChatRequest(
            executionContext(),
            new ConversationSnapshot(
                List.of(userMessage("What time is it?"), first.assistantMessage()), List.of())));

    final var captor =
        ArgumentCaptor.forClass(dev.langchain4j.model.chat.request.ChatRequest.class);
    verify(l4jModel, times(2)).chat(captor.capture());
    assertThat(captor.getAllValues().get(1).messages())
        .containsExactly(
            UserMessage.from("What time is it?"),
            AiMessage.builder()
                .toolExecutionRequests(List.of(toolCall))
                .attributes(Map.of("signature_call-1", "abc"))
                .build());
  }

  @Test
  void doesNotRestoreToolCallMetadataThatAnotherProviderPersisted() {
    final var chatModel =
        new SignatureFactory(l4jModel).create(new CustomConfiguration("my-model"));
    when(l4jModel.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class)))
        .thenReturn(
            ChatResponse.builder()
                .aiMessage(AiMessage.builder().text("It is noon.").build())
                .build());

    chatModel.execute(
        new ChatRequest(
            executionContext(),
            new ConversationSnapshot(
                List.of(
                    userMessage("What time is it?"),
                    AssistantMessage.builder()
                        .toolCalls(
                            List.of(
                                new ToolCall(
                                    "call-1",
                                    "GetTime",
                                    Map.of(),
                                    Map.of("another-provider", Map.of("signature", "abc")))))
                        .build()),
                List.of())));

    final var captor =
        ArgumentCaptor.forClass(dev.langchain4j.model.chat.request.ChatRequest.class);
    verify(l4jModel).chat(captor.capture());
    assertThat(captor.getValue().messages())
        .containsExactly(
            UserMessage.from("What time is it?"),
            AiMessage.builder()
                .toolExecutionRequests(
                    List.of(
                        ToolExecutionRequest.builder()
                            .id("call-1")
                            .name("GetTime")
                            .arguments("{}")
                            .build()))
                .build());
  }

  @Test
  void resolvesOnlyTheConfigurationsThatTheFactorySupports() {
    assertThat(factory.supports(new CustomConfiguration("my-model"))).isTrue();

    final var other = new OtherConfiguration("other-model");
    assertThat(factory.supports(other)).isFalse();
    assertThatThrownBy(() -> registry.resolve(other))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "No chat model registered for configuration: provider=other-provider, model=other-model");
  }

  @Test
  void routesACustomProviderConfigurationToTheFactoryOfItsProviderType() {
    final var acmeModel = mock(CloseableChatModel.class);
    final var otherModel = mock(CloseableChatModel.class);
    final var acmeFactory = new CustomProviderFactory("acme", acmeModel);
    final var otherFactory = new CustomProviderFactory("other", otherModel);
    final var customRegistry =
        new ChatModelRegistryImpl(List.<ChatModelFactory>of(acmeFactory, otherFactory));

    final var acmeConfiguration = new CustomProviderConfiguration("acme", "acme-model", Map.of());
    final var otherConfiguration =
        new CustomProviderConfiguration("other", "other-model", Map.of());
    final var unknownConfiguration =
        new CustomProviderConfiguration("unknown", "unknown-model", Map.of());

    assertThat(acmeFactory.supports(acmeConfiguration)).isTrue();
    assertThat(acmeFactory.supports(otherConfiguration)).isFalse();
    assertThat(otherFactory.supports(acmeConfiguration)).isFalse();
    assertThat(otherFactory.supports(otherConfiguration)).isTrue();
    assertThat(acmeFactory.supports(new CustomConfiguration("my-model"))).isFalse();

    customRegistry.resolve(acmeConfiguration).close();
    verify(acmeModel).close();
    verifyNoInteractions(otherModel);

    customRegistry.resolve(otherConfiguration).close();
    verify(otherModel).close();
    verify(acmeModel, times(1)).close();

    assertThatThrownBy(() -> customRegistry.resolve(unknownConfiguration))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "No chat model registered for configuration: provider=unknown, model=unknown-model");
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
      return PROVIDER;
    }
  }

  private record OtherConfiguration(String model) implements ChatModelConfiguration {
    @Override
    public String provider() {
      return "other-provider";
    }
  }

  private static ChatMessageConverterImpl chatMessageConverter() {
    return new ChatMessageConverterImpl(
        new ContentConverterImpl(OBJECT_MAPPER, new DocumentToContentConverterImpl()),
        new ToolCallConverterImpl(OBJECT_MAPPER),
        OBJECT_MAPPER);
  }

  private static class CustomFactory implements ChatModelFactory {

    private final CloseableChatModel model;

    CustomFactory(CloseableChatModel model) {
      this.model = model;
    }

    @Override
    public boolean supports(ChatModelConfiguration configuration) {
      return configuration instanceof CustomConfiguration;
    }

    @Override
    public ChatModel create(ChatModelConfiguration configuration) {
      return new LangChain4JChatModel(
          model,
          chatMessageConverter(),
          new ToolSpecificationConverterImpl(new JsonSchemaConverter(OBJECT_MAPPER)),
          new JsonSchemaConverter(OBJECT_MAPPER));
    }
  }

  /** Carries a signature through the tool call metadata, keyed by the tool call ID. */
  private static class SignatureFactory extends CustomFactory {

    private final CloseableChatModel model;

    SignatureFactory(CloseableChatModel model) {
      super(model);
      this.model = model;
    }

    @Override
    public ChatModel create(ChatModelConfiguration configuration) {
      return new LangChain4JChatModel(
          model,
          chatMessageConverter(),
          new ToolSpecificationConverterImpl(new JsonSchemaConverter(OBJECT_MAPPER)),
          new JsonSchemaConverter(OBJECT_MAPPER),
          new ToolCallMetadataCodec(
              configuration.provider(),
              (toolCallId, attributes) ->
                  attributes.get("signature_" + toolCallId) instanceof String signature
                      ? Map.of("signature", signature)
                      : Map.of(),
              (toolCallId, providerMetadata) ->
                  providerMetadata.get("signature") instanceof String signature
                      ? Map.of("signature_" + toolCallId, signature)
                      : Map.of()),
          LangChain4JChatModel::mapTokenUsage);
    }
  }

  /** A factory for the custom providers of one provider type. */
  private static class CustomProviderFactory implements ChatModelFactory {

    private final String providerType;
    private final CloseableChatModel model;

    CustomProviderFactory(String providerType, CloseableChatModel model) {
      this.providerType = providerType;
      this.model = model;
    }

    @Override
    public boolean supports(ChatModelConfiguration configuration) {
      return configuration instanceof CustomChatModelConfiguration custom
          && providerType.equals(custom.providerType());
    }

    @Override
    public ChatModel create(ChatModelConfiguration configuration) {
      return new LangChain4JChatModel(
          model,
          chatMessageConverter(),
          new ToolSpecificationConverterImpl(new JsonSchemaConverter(OBJECT_MAPPER)),
          new JsonSchemaConverter(OBJECT_MAPPER));
    }
  }
}
