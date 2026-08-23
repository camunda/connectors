/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.autoconfigure;

import static io.camunda.connector.agenticai.autoconfigure.ApplicationContextAssertions.assertDoesNotHaveAnyBeansOf;
import static io.camunda.connector.agenticai.autoconfigure.ApplicationContextAssertions.assertHasAllBeansOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.connector.agenticai.adhoctoolsschema.AdHocToolsSchemaFunction;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.CachingProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.CamundaClientProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.feel.AdHocToolElementParameterExtractor;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.AdHocToolSchemaGenerator;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.AdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.aiagent.AgentSubProcessV1Function;
import io.camunda.connector.agenticai.aiagent.AgentSubProcessV2Function;
import io.camunda.connector.agenticai.aiagent.AgentTaskV1Function;
import io.camunda.connector.agenticai.aiagent.AgentTaskV2Function;
import io.camunda.connector.agenticai.aiagent.agent.AgentConversationTurnInputComposer;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializer;
import io.camunda.connector.agenticai.aiagent.agent.AgentResponseHandler;
import io.camunda.connector.agenticai.aiagent.agent.AgentSubProcessRequestHandler;
import io.camunda.connector.agenticai.aiagent.agent.AgentTaskRequestHandler;
import io.camunda.connector.agenticai.aiagent.agent.AgentToolsResolver;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceClient;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRegistry;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicMessageRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicMessageResponseConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock.BedrockConverseChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini.GeminiChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ChatMessageConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.document.DocumentToContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolCallConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiChatModelFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiFoundryCredentialResolver;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.OpenAiApiFamilyStrategy;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.AwsAgentCoreConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.mapping.AwsAgentCoreConversationMapper;
import io.camunda.connector.agenticai.aiagent.memory.conversation.document.CamundaDocumentConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationStore;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicBackend.AnthropicApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.AnthropicChatModelConfiguration.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiBackend.GeminiApiBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.GeminiChatModelConfiguration.GeminiModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.common.AgenticAiHttpProxySupport;
import io.camunda.connector.http.client.proxy.EnvironmentProxyConfiguration;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.validation.FieldError;

class AgenticAiConnectorsAutoConfigurationTest {

  private static final List<Class<?>> AGENTIC_AI_BEANS =
      List.of(
          AgenticAiHttpProxySupport.class,
          AdHocToolElementParameterExtractor.class,
          AdHocToolSchemaGenerator.class,
          AdHocToolsSchemaResolver.class,
          ProcessDefinitionAdHocToolElementsResolver.class,
          AdHocToolsSchemaFunction.class,
          GatewayToolHandlerRegistry.class,
          AgentToolsResolver.class,
          AgentInitializer.class,
          InProcessConversationStore.class,
          CamundaDocumentConversationStore.class,
          AwsAgentCoreConversationMapper.class,
          AwsAgentCoreConversationStore.class,
          ConversationStoreRegistry.class,
          AgentConversationTurnInputComposer.class,
          AgentResponseHandler.class,
          AgentTaskRequestHandler.class,
          AgentTaskV1Function.class,
          AgentTaskV2Function.class,
          AgentSubProcessRequestHandler.class,
          AgentSubProcessV1Function.class,
          AgentSubProcessV2Function.class,
          AgentInstanceClient.class,
          ChatModelRegistry.class,
          AnthropicChatModelFactory.class,
          BedrockConverseChatModelFactory.class,
          OpenAiChatModelFactory.class,
          GeminiChatModelFactory.class);

  private static final List<Class<?>> LANGCHAIN4J_BEANS =
      List.of(
          ChatModelHttpProxySupport.class,
          DocumentToContentConverter.class,
          ContentConverter.class,
          ToolCallConverter.class,
          JsonSchemaConverter.class,
          ToolSpecificationConverter.class,
          ChatMessageConverter.class);

  // this will need to be updated in case we support different frameworks
  private static final List<Class<?>> ALL_BEANS =
      Stream.concat(AGENTIC_AI_BEANS.stream(), LANGCHAIN4J_BEANS.stream()).toList();

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TestConfig.class)
          .withUserConfiguration(AgenticAiConnectorsAutoConfiguration.class);

  @Test
  void whenAgenticAiConfigurationEnabled_thenAgenticConnectorBeansAreCreated() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.enabled=true")
        .run(context -> assertHasAllBeansOf(context, ALL_BEANS));
  }

  @Test
  void whenAgenticAiConfigurationDisabled_thenNoAgenticConnectorBeansAreCreated() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.enabled=false")
        .run(context -> assertDoesNotHaveAnyBeansOf(context, ALL_BEANS));
  }

  @Test
  void whenAgentTaskConnectorDisabled_thenNoAgentTaskFunctionIsCreated() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.aiagent.outbound-connector.enabled=false")
        .run(
            context -> {
              assertHasAllBeansOf(
                  context,
                  ALL_BEANS.stream()
                      .filter(
                          notAnyOf(
                              AgentTaskRequestHandler.class,
                              AgentTaskV1Function.class,
                              AgentTaskV2Function.class))
                      .toList());
              assertThat(context)
                  .doesNotHaveBean(AgentTaskRequestHandler.class)
                  .doesNotHaveBean(AgentTaskV1Function.class)
                  .doesNotHaveBean(AgentTaskV2Function.class);
            });
  }

  @Test
  void whenAgentSubProcessConnectorDisabled_thenNoAgentSubProcessFunctionIsCreated() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.aiagent.job-worker.enabled=false")
        .run(
            context -> {
              assertHasAllBeansOf(
                  context,
                  ALL_BEANS.stream()
                      .filter(
                          notAnyOf(
                              AgentSubProcessRequestHandler.class,
                              AgentSubProcessV1Function.class,
                              AgentSubProcessV2Function.class))
                      .toList());
              assertThat(context)
                  .doesNotHaveBean(AgentSubProcessRequestHandler.class)
                  .doesNotHaveBean(AgentSubProcessV1Function.class)
                  .doesNotHaveBean(AgentSubProcessV2Function.class);
            });
  }

  @Test
  void whenAdHocToolsSchemaConnectorDisabled_thenNoAdHocToolsSchemaFunctionIsCreated() {
    contextRunner
        .withPropertyValues(
            "camunda.connector.agenticai.ad-hoc-tools-schema-resolver.enabled=false")
        .run(
            context -> {
              assertHasAllBeansOf(
                  context,
                  ALL_BEANS.stream().filter(notAnyOf(AdHocToolsSchemaFunction.class)).toList());
              assertThat(context).doesNotHaveBean(AdHocToolsSchemaFunction.class);
            });
  }

  @Test
  void whenToolsCachingDisabled_thenConfiguresDefaultToolElementsResolver() {
    contextRunner
        .withPropertyValues(
            "camunda.connector.agenticai.tools.process-definition.cache.enabled=false")
        .run(
            context ->
                assertThat(context)
                    .getBean(ProcessDefinitionAdHocToolElementsResolver.class)
                    .isInstanceOf(CamundaClientProcessDefinitionAdHocToolElementsResolver.class));
  }

  @Test
  void whenToolsCachingEnabled_thenConfiguresCachingToolElementsResolver() {
    contextRunner
        .withPropertyValues(
            "camunda.connector.agenticai.tools.process-definition.cache.enabled=true")
        .run(
            context ->
                assertThat(context)
                    .getBean(ProcessDefinitionAdHocToolElementsResolver.class)
                    .isInstanceOf(CachingProcessDefinitionAdHocToolElementsResolver.class));
  }

  @Test
  void whenToolsCachingMaximumSizeIsNegative_thenFailsValidation() {
    contextRunner
        .withPropertyValues(
            "camunda.connector.agenticai.tools.process-definition.cache.maximum-size=-10")
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .hasRootCauseInstanceOf(BindValidationException.class)
                    .rootCause()
                    .isInstanceOfSatisfying(
                        BindValidationException.class,
                        e -> {
                          assertThat(e.getValidationErrors().getAllErrors())
                              .hasSize(1)
                              .first(InstanceOfAssertFactories.type(FieldError.class))
                              .extracting(
                                  FieldError::getObjectName,
                                  FieldError::getField,
                                  FieldError::getRejectedValue,
                                  FieldError::getDefaultMessage)
                              .containsExactly(
                                  "camunda.connector.agenticai",
                                  "tools.processDefinition.cache.maximumSize",
                                  -10L,
                                  "must be greater than or equal to 0");
                        }));
  }

  @Test
  void whenProxySupportEnabled_thenAgenticAiHttpProxySupportUsesEnvironmentProxyConfiguration() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(AgenticAiHttpProxySupport.class);
          var httpProxySupport = context.getBean(AgenticAiHttpProxySupport.class);
          assertThat(httpProxySupport.getProxyConfiguration())
              .isInstanceOf(EnvironmentProxyConfiguration.class);
        });
  }

  @Test
  void whenProxySupportDisabled_thenAgenticAiHttpProxySupportUsesNoProxyConfiguration() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.http.proxy-support.enabled=false")
        .run(
            context -> {
              assertThat(context).hasSingleBean(AgenticAiHttpProxySupport.class);
              var httpProxySupport = context.getBean(AgenticAiHttpProxySupport.class);

              final var proxyConfiguration = httpProxySupport.getProxyConfiguration();
              assertThat(proxyConfiguration).isNotInstanceOf(EnvironmentProxyConfiguration.class);

              assertThat(httpProxySupport.getProxyConfiguration().getProxyDetails("http"))
                  .isEmpty();
              assertThat(httpProxySupport.getProxyConfiguration().getProxyDetails("https"))
                  .isEmpty();
            });
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("chatModelResolutionCases")
  void resolvesChatModelForConfiguration(ChatModelResolutionCase resolutionCase) {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(resolutionCase.factoryClass());

          final var chatModelRegistry = context.getBean(ChatModelRegistry.class);
          try (var chatModel = chatModelRegistry.resolve(resolutionCase.configuration())) {
            assertThat(chatModel).isNotNull();
          }
        });
  }

  static Stream<ChatModelResolutionCase> chatModelResolutionCases() {
    return Stream.of(
        new ChatModelResolutionCase(
            "anthropic (native)",
            new AnthropicChatModelConfiguration(
                new AnthropicConnection(
                    new AnthropicApiBackend(
                        new AnthropicApiBackend.AnthropicApi(
                            "sk-ant-test", null, null, null, null)),
                    new AnthropicModel("claude-sonnet-5", null),
                    null)),
            AnthropicChatModelFactory.class),
        new ChatModelResolutionCase(
            "google-gemini (native)",
            new GeminiChatModelConfiguration(
                new GeminiConnection(
                    new GeminiApiBackend(
                        new GeminiApiBackend.GoogleGeminiApi("test-api-key", null)),
                    new GeminiModel("gemini-3-pro-preview", null),
                    null)),
            GeminiChatModelFactory.class),
        new ChatModelResolutionCase(
            "openai (native)",
            new OpenAiChatModelConfiguration(
                new OpenAiChatModelConfiguration.OpenAiConnection(
                    new OpenAiChatModelConfiguration.OpenAiApi.OpenAiResponsesApi(
                        new OpenAiChatModelConfiguration.OpenAiApi.OpenAiResponsesApi
                            .ResponsesParameters(null, null, null, null)),
                    new OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend(
                        new OpenAiChatModelConfiguration.OpenAiBackend.OpenAiApiBackend
                            .OpenAiApiConnection(
                            "sk-openai-test", null, null, null, null, null, null)),
                    new OpenAiChatModelConfiguration.OpenAiModel("gpt-5.5"),
                    null)),
            OpenAiChatModelFactory.class));
  }

  record ChatModelResolutionCase(
      String label,
      ChatModelConfiguration configuration,
      Class<? extends ChatModelFactory> factoryClass) {

    @Override
    public String toString() {
      return label;
    }
  }

  @Nested
  class CustomNativeChatModelFactoryOverrides {

    @ParameterizedTest
    @MethodSource("nativeFactoryOverrideCases")
    void userProvidedFactoryBeanOverridesDefault(NativeFactoryOverrideCase override) {
      new ApplicationContextRunner()
          .withUserConfiguration(TestConfig.class, override.configurationClass())
          .withUserConfiguration(AgenticAiConnectorsAutoConfiguration.class)
          .run(
              context -> {
                final var beanNamesForType = context.getBeanNamesForType(override.factoryClass());
                assertThat(beanNamesForType).hasSize(1).containsExactly(override.beanName());

                assertThat(context.getBean(beanNamesForType[0]))
                    .isInstanceOf(override.customFactoryClass());
              });
    }

    static Stream<NativeFactoryOverrideCase> nativeFactoryOverrideCases() {
      return Stream.of(
          new NativeFactoryOverrideCase(
              CustomAnthropicProviderConfig.class,
              "customNativeAnthropicChatModelFactory",
              AnthropicChatModelFactory.class,
              CustomAnthropicProviderConfig.CustomAnthropicChatModelFactory.class),
          new NativeFactoryOverrideCase(
              CustomOpenAiProviderConfig.class,
              "customNativeOpenAiChatModelFactory",
              OpenAiChatModelFactory.class,
              CustomOpenAiProviderConfig.CustomOpenAiChatModelFactory.class));
    }

    record NativeFactoryOverrideCase(
        Class<?> configurationClass,
        String beanName,
        Class<? extends ChatModelFactory> factoryClass,
        Class<? extends ChatModelFactory> customFactoryClass) {

      @Override
      public String toString() {
        return factoryClass.getSimpleName();
      }
    }

    static class CustomAnthropicProviderConfig {
      @Bean
      AnthropicChatModelFactory customNativeAnthropicChatModelFactory() {
        return new CustomAnthropicChatModelFactory();
      }

      static class CustomAnthropicChatModelFactory extends AnthropicChatModelFactory {

        CustomAnthropicChatModelFactory() {
          super(
              mock(AgenticAiHttpProxySupport.class),
              mock(AnthropicMessageRequestConverter.class),
              mock(AnthropicMessageResponseConverter.class));
        }

        @Override
        public ChatModel create(ChatModelConfiguration configuration) {
          return mock(ChatModel.class);
        }
      }
    }

    static class CustomOpenAiProviderConfig {
      @Bean
      OpenAiChatModelFactory customNativeOpenAiChatModelFactory() {
        return new CustomOpenAiChatModelFactory();
      }

      static class CustomOpenAiChatModelFactory extends OpenAiChatModelFactory {

        CustomOpenAiChatModelFactory() {
          super(
              mock(AgenticAiHttpProxySupport.class),
              mock(OpenAiApiFamilyStrategy.class),
              mock(OpenAiApiFamilyStrategy.class),
              mock(OpenAiFoundryCredentialResolver.class));
        }

        @Override
        public ChatModel create(ChatModelConfiguration configuration) {
          return mock(ChatModel.class);
        }
      }
    }
  }

  private Predicate<Class<?>> notAnyOf(Class<?>... classes) {
    return c -> Stream.of(classes).noneMatch(c::equals);
  }
}
