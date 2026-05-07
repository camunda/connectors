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

import dev.langchain4j.model.chat.ChatModel;
import io.camunda.connector.agenticai.adhoctoolsschema.AdHocToolsSchemaFunction;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.CachingProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.CamundaClientProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.feel.AdHocToolElementParameterExtractor;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.AdHocToolSchemaGenerator;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.AdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.aiagent.AiAgentFunction;
import io.camunda.connector.agenticai.aiagent.AiAgentJobWorker;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializer;
import io.camunda.connector.agenticai.aiagent.agent.AgentLimitsValidator;
import io.camunda.connector.agenticai.aiagent.agent.AgentMessagesHandler;
import io.camunda.connector.agenticai.aiagent.agent.AgentResponseHandler;
import io.camunda.connector.agenticai.aiagent.agent.AgentToolsResolver;
import io.camunda.connector.agenticai.aiagent.agent.JobWorkerAgentRequestHandler;
import io.camunda.connector.agenticai.aiagent.agent.OutboundConnectorAgentRequestHandler;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatClient;
import io.camunda.connector.agenticai.aiagent.framework.api.ChatModelApiRegistry;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatMessageConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ContentConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.document.DocumentToContentConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.AzureOpenAiChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.BedrockChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProviderRegistry;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.GoogleVertexAiChatModelProvider;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolCallConverter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.AwsAgentCoreConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.mapping.AwsAgentCoreConversationMapper;
import io.camunda.connector.agenticai.aiagent.memory.conversation.document.CamundaDocumentConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationStore;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureOpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.GoogleVertexAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsAutoConfigurationTest.CustomChatModelProviderOverrides.CustomAzureOpenAiProviderConfig.CustomAzureOpenAiChatModelProvider;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsAutoConfigurationTest.CustomChatModelProviderOverrides.CustomBedrockProviderConfig.CustomBedrockChatModelProvider;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsAutoConfigurationTest.CustomChatModelProviderOverrides.CustomGoogleVertexAiProviderConfig.CustomGoogleVertexAiChatModelProvider;
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
import org.springframework.core.ResolvableType;
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
          AgentLimitsValidator.class,
          AgentMessagesHandler.class,
          AgentResponseHandler.class,
          ChatModelApiRegistry.class,
          ChatClient.class,
          OutboundConnectorAgentRequestHandler.class,
          AiAgentFunction.class,
          JobWorkerAgentRequestHandler.class,
          AiAgentJobWorker.class);

  // L4J factory + provider beans for `anthropic`, `openai`, `openaiCompatible` were dropped when
  // those providers landed native in ADR-005 Phase B/C/D — only the still-bridged providers
  // (Bedrock, Azure OpenAI, Google Vertex AI) keep an L4J `ChatModelProvider` bean.
  private static final List<Class<?>> LANGCHAIN4J_BEANS =
      List.of(
          ChatModelHttpProxySupport.class,
          AzureOpenAiChatModelProvider.class,
          BedrockChatModelProvider.class,
          GoogleVertexAiChatModelProvider.class,
          ChatModelProviderRegistry.class,
          ChatModelFactory.class,
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
  void whenAiAgentConnectorDisabled_thenNoAiAgentFunctionIsCreated() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.aiagent.outbound-connector.enabled=false")
        .run(
            context -> {
              assertHasAllBeansOf(
                  context,
                  ALL_BEANS.stream()
                      .filter(
                          notAnyOf(
                              OutboundConnectorAgentRequestHandler.class, AiAgentFunction.class))
                      .toList());
              assertThat(context)
                  .doesNotHaveBean(OutboundConnectorAgentRequestHandler.class)
                  .doesNotHaveBean(AiAgentFunction.class);
            });
  }

  @Test
  void whenAiAgentJobWorkerConnectorDisabled_thenNoAiAgentJobWorkerIsCreated() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.aiagent.job-worker.enabled=false")
        .run(
            context -> {
              assertHasAllBeansOf(
                  context,
                  ALL_BEANS.stream()
                      .filter(notAnyOf(JobWorkerAgentRequestHandler.class, AiAgentJobWorker.class))
                      .toList());
              assertThat(context)
                  .doesNotHaveBean(JobWorkerAgentRequestHandler.class)
                  .doesNotHaveBean(AiAgentJobWorker.class);
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

  @Nested
  class CustomChatModelProviderOverrides {

    @ParameterizedTest
    @MethodSource("providerOverrideCases")
    void userProvidedProviderBeanOverridesDefault(ProviderOverrideCase override) {
      new ApplicationContextRunner()
          .withUserConfiguration(TestConfig.class, override.configurationClass())
          .withUserConfiguration(AgenticAiConnectorsAutoConfiguration.class)
          .run(
              context -> {
                ResolvableType type =
                    ResolvableType.forClassWithGenerics(
                        ChatModelProvider.class, override.providerConfigurationClass());

                final var beanNamesForType = context.getBeanNamesForType(type);
                assertThat(beanNamesForType).hasSize(1).containsExactly(override.beanName());

                assertThat(context.getBean(beanNamesForType[0]))
                    .isInstanceOf(override.customProviderClass());
              });
    }

    static Stream<ProviderOverrideCase> providerOverrideCases() {
      // Only providers still backed by the L4J bridge declare a `ChatModelProvider<X>` bean and
      // therefore support overriding it. `anthropic`, `openai`, and `openaiCompatible` have native
      // factories now and no L4J `ChatModelProvider<X>` to replace.
      return Stream.of(
          new ProviderOverrideCase(
              CustomAzureOpenAiProviderConfig.class,
              "customAzureOpenAiChatModelProvider",
              AzureOpenAiProviderConfiguration.class,
              CustomAzureOpenAiChatModelProvider.class),
          new ProviderOverrideCase(
              CustomBedrockProviderConfig.class,
              "customBedrockChatModelProvider",
              BedrockProviderConfiguration.class,
              CustomBedrockChatModelProvider.class),
          new ProviderOverrideCase(
              CustomGoogleVertexAiProviderConfig.class,
              "customGoogleVertexAiChatModelProvider",
              GoogleVertexAiProviderConfiguration.class,
              CustomGoogleVertexAiChatModelProvider.class));
    }

    record ProviderOverrideCase(
        Class<?> configurationClass,
        String beanName,
        Class<? extends ProviderConfiguration> providerConfigurationClass,
        Class<? extends ChatModelProvider<?>> customProviderClass) {

      @Override
      public String toString() {
        return providerConfigurationClass.getSimpleName();
      }
    }

    static class CustomAzureOpenAiProviderConfig {
      @Bean
      ChatModelProvider<AzureOpenAiProviderConfiguration> customAzureOpenAiChatModelProvider() {
        return new CustomAzureOpenAiChatModelProvider();
      }

      static class CustomAzureOpenAiChatModelProvider
          implements ChatModelProvider<AzureOpenAiProviderConfiguration> {

        @Override
        public String type() {
          return AzureOpenAiProviderConfiguration.AZURE_OPENAI_ID;
        }

        @Override
        public ChatModel createChatModel(AzureOpenAiProviderConfiguration providerConfiguration) {
          return mock(ChatModel.class);
        }
      }
    }

    static class CustomBedrockProviderConfig {
      @Bean
      ChatModelProvider<BedrockProviderConfiguration> customBedrockChatModelProvider() {
        return new CustomBedrockChatModelProvider();
      }

      static class CustomBedrockChatModelProvider
          implements ChatModelProvider<BedrockProviderConfiguration> {

        @Override
        public String type() {
          return BedrockProviderConfiguration.BEDROCK_ID;
        }

        @Override
        public ChatModel createChatModel(BedrockProviderConfiguration providerConfiguration) {
          return mock(ChatModel.class);
        }
      }
    }

    static class CustomGoogleVertexAiProviderConfig {
      @Bean
      ChatModelProvider<GoogleVertexAiProviderConfiguration>
          customGoogleVertexAiChatModelProvider() {
        return new CustomGoogleVertexAiChatModelProvider();
      }

      static class CustomGoogleVertexAiChatModelProvider
          implements ChatModelProvider<GoogleVertexAiProviderConfiguration> {

        @Override
        public String type() {
          return GoogleVertexAiProviderConfiguration.GOOGLE_VERTEX_AI_ID;
        }

        @Override
        public ChatModel createChatModel(
            GoogleVertexAiProviderConfiguration providerConfiguration) {
          return mock(ChatModel.class);
        }
      }
    }
  }

  private Predicate<Class<?>> notAnyOf(Class<?>... classes) {
    return c -> Stream.of(classes).noneMatch(c::equals);
  }
}
