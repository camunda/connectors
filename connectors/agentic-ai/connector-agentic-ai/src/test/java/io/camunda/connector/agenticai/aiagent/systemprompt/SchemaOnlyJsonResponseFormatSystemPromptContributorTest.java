/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.systemprompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.request.AgentTaskResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.TextResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AwsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockConverseChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockConverseChatModelConfiguration.BedrockConverseConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.BedrockConverseChatModelConfiguration.BedrockConverseModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.CustomProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiApiBackend.GoogleGeminiApi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiCompletionsApi;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiApi.OpenAiCompletionsApi.CompletionsParameters;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend.OpenAiApiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration.OpenAiModel;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SchemaOnlyJsonResponseFormatSystemPromptContributorTest {

  private static final AgentContext CTX = AgentContext.builder().state(AgentState.READY).build();

  private final SchemaOnlyJsonResponseFormatSystemPromptContributor contributor =
      new SchemaOnlyJsonResponseFormatSystemPromptContributor();

  private static AnthropicChatModelConfiguration anthropicModel() {
    return new AnthropicChatModelConfiguration(
        new AnthropicConnection(
            new AnthropicApiBackend(
                new AnthropicApiBackend.AnthropicApi("sk-ant-test", null, null, null, null)),
            new AnthropicModel("claude-sonnet-4-6", null),
            null));
  }

  private static BedrockConverseChatModelConfiguration bedrockModel() {
    return new BedrockConverseChatModelConfiguration(
        new BedrockConverseConnection(
            "eu-central-1",
            null,
            new AwsAuthentication.AwsDefaultCredentialsChainAuthentication(),
            null,
            null,
            null,
            null,
            new BedrockConverseModel("us.amazon.nova-2-lite-v1:0", null)));
  }

  private static OpenAiChatModelConfiguration openAiModel() {
    return new OpenAiChatModelConfiguration(
        new OpenAiConnection(
            new OpenAiCompletionsApi(new CompletionsParameters(null, null, null, null)),
            new OpenAiApiBackend(
                new OpenAiApiConnection("sk-test", null, null, null, null, null, null)),
            new OpenAiModel("gpt-4o"),
            null));
  }

  private static GeminiChatModelConfiguration geminiModel() {
    return new GeminiChatModelConfiguration(
        new GeminiConnection(
            new GeminiApiBackend(new GoogleGeminiApi("gm-test", null)),
            new GeminiModel("gemini-3-pro-preview", null),
            null));
  }

  private static CustomProviderConfiguration customModel() {
    return new CustomProviderConfiguration("some-custom-provider", "some-model", Map.of());
  }

  private static AgentExecutionContext executionContext(
      ChatModelConfiguration chatModel, ResponseConfiguration response) {
    var configuration = new AgentConfiguration(chatModel, null, null, null, null, null, response);
    var executionContext = mock(AgentExecutionContext.class);
    when(executionContext.configuration()).thenReturn(configuration);
    return executionContext;
  }

  @ParameterizedTest
  @MethodSource("schemaOnlyProvidersWithoutSchema")
  void contributesInstructionForSchemaOnlyProvidersWithoutSchema(ChatModelConfiguration chatModel) {
    var response =
        new AgentTaskResponseConfiguration(new JsonResponseFormatConfiguration(null, null), false);
    var executionContext = executionContext(chatModel, response);

    var result = contributor.contribute(executionContext, CTX);

    assertThat(result).isEqualTo(SchemaOnlyJsonResponseFormatSystemPromptContributor.INSTRUCTION);
  }

  @ParameterizedTest
  @MethodSource("schemaOnlyProvidersWithoutSchema")
  void contributesInstructionForSchemaOnlyProvidersWithEmptySchema(
      ChatModelConfiguration chatModel) {
    var response =
        new AgentTaskResponseConfiguration(
            new JsonResponseFormatConfiguration(Map.of(), null), false);
    var executionContext = executionContext(chatModel, response);

    var result = contributor.contribute(executionContext, CTX);

    assertThat(result).isEqualTo(SchemaOnlyJsonResponseFormatSystemPromptContributor.INSTRUCTION);
  }

  static Stream<Arguments> schemaOnlyProvidersWithoutSchema() {
    return Stream.of(Arguments.of(anthropicModel()), Arguments.of(bedrockModel()));
  }

  @ParameterizedTest
  @MethodSource("nonSchemaOnlyProviders")
  void doesNotContributeForProvidersWithNativeSchemaLessJsonMode(ChatModelConfiguration chatModel) {
    var response =
        new AgentTaskResponseConfiguration(new JsonResponseFormatConfiguration(null, null), false);
    var executionContext = executionContext(chatModel, response);

    var result = contributor.contribute(executionContext, CTX);

    assertThat(result).isNull();
  }

  static Stream<Arguments> nonSchemaOnlyProviders() {
    return Stream.of(
        Arguments.of(openAiModel()), Arguments.of(geminiModel()), Arguments.of(customModel()));
  }

  @ParameterizedTest
  @MethodSource("schemaOnlyProvidersWithoutSchema")
  void doesNotContributeWhenSchemaIsPresent(ChatModelConfiguration chatModel) {
    var response =
        new AgentTaskResponseConfiguration(
            new JsonResponseFormatConfiguration(Map.of("type", "object"), null), false);
    var executionContext = executionContext(chatModel, response);

    var result = contributor.contribute(executionContext, CTX);

    assertThat(result).isNull();
  }

  @ParameterizedTest
  @MethodSource("schemaOnlyProvidersWithoutSchema")
  void doesNotContributeForTextResponseFormat(ChatModelConfiguration chatModel) {
    var response =
        new AgentTaskResponseConfiguration(new TextResponseFormatConfiguration(false), false);
    var executionContext = executionContext(chatModel, response);

    var result = contributor.contribute(executionContext, CTX);

    assertThat(result).isNull();
  }

  @ParameterizedTest
  @MethodSource("schemaOnlyProvidersWithoutSchema")
  void doesNotContributeWhenResponseConfigurationIsNull(ChatModelConfiguration chatModel) {
    var executionContext = executionContext(chatModel, null);

    var result = contributor.contribute(executionContext, CTX);

    assertThat(result).isNull();
  }
}
