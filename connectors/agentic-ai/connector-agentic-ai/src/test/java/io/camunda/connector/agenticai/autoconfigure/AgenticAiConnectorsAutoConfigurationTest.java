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
import io.camunda.connector.agenticai.aiagent.AiAgentFunction;
import io.camunda.connector.agenticai.aiagent.AiAgentJobWorker;
import io.camunda.connector.agenticai.aiagent.AiAgentSubProcessV2Function;
import io.camunda.connector.agenticai.aiagent.AiAgentTaskV2Function;
import io.camunda.connector.agenticai.aiagent.agent.AgentConversationTurnInputComposer;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializer;
import io.camunda.connector.agenticai.aiagent.agent.AgentResponseHandler;
import io.camunda.connector.agenticai.aiagent.agent.AgentToolsResolver;
import io.camunda.connector.agenticai.aiagent.agent.JobWorkerAgentRequestHandler;
import io.camunda.connector.agenticai.aiagent.agent.OutboundConnectorAgentRequestHandler;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceClient;
import io.camunda.connector.agenticai.aiagent.capabilities.CapabilityMatrix;
import io.camunda.connector.agenticai.aiagent.capabilities.ModelCapabilitiesResolver;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelApiRegistry;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ChatMessageConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.CloseableChatModel;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.ContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.Langchain4JChatModelApi;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.document.DocumentToContentConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.Langchain4JAnthropicChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.Langchain4JAzureOpenAiChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.Langchain4JBedrockChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.Langchain4JChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.Langchain4JGoogleVertexAiChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.Langchain4JOpenAiChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.factory.Langchain4JOpenAiCompatibleChatModelApiFactory;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.jsonschema.JsonSchemaConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolCallConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.tool.ToolSpecificationConverter;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.AwsAgentCoreConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.mapping.AwsAgentCoreConversationMapper;
import io.camunda.connector.agenticai.aiagent.memory.conversation.document.CamundaDocumentConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationStore;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AnthropicProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.AzureOpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.BedrockProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.GoogleVertexAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.OpenAiCompatibleProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v1.OpenAiProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.aiagent.transport.HttpTransportSupport;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsAutoConfigurationTest.CustomChatModelApiFactoryOverrides.CustomAnthropicProviderConfig.CustomAnthropicChatModelApiFactory;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsAutoConfigurationTest.CustomChatModelApiFactoryOverrides.CustomAzureOpenAiProviderConfig.CustomAzureOpenAiChatModelApiFactory;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsAutoConfigurationTest.CustomChatModelApiFactoryOverrides.CustomBedrockProviderConfig.CustomBedrockChatModelApiFactory;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsAutoConfigurationTest.CustomChatModelApiFactoryOverrides.CustomGoogleVertexAiProviderConfig.CustomGoogleVertexAiChatModelApiFactory;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsAutoConfigurationTest.CustomChatModelApiFactoryOverrides.CustomOpenAiCompatibleProviderConfig.CustomOpenAiCompatibleChatModelApiFactory;
import io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsAutoConfigurationTest.CustomChatModelApiFactoryOverrides.CustomOpenAiProviderConfig.CustomOpenAiChatModelApiFactory;
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
          HttpTransportSupport.class,
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
          OutboundConnectorAgentRequestHandler.class,
          AiAgentFunction.class,
          AiAgentTaskV2Function.class,
          JobWorkerAgentRequestHandler.class,
          AiAgentJobWorker.class,
          AiAgentSubProcessV2Function.class,
          AgentInstanceClient.class,
          ChatModelApiRegistry.class,
          CapabilityMatrix.class,
          ModelCapabilitiesResolver.class);

  private static final List<Class<?>> LANGCHAIN4J_BEANS =
      List.of(
          ChatModelHttpProxySupport.class,
          DocumentToContentConverter.class,
          ContentConverter.class,
          ToolCallConverter.class,
          JsonSchemaConverter.class,
          ToolSpecificationConverter.class,
          ChatMessageConverter.class);

  // One Langchain4JChatModelApiFactory bean is registered per built-in provider (six today), so
  // this type has multiple beans and cannot be asserted via assertHasAllBeansOf/hasSingleBean like
  // the rest of LANGCHAIN4J_BEANS; it is asserted separately (see the dedicated tests below).
  private static final int LANGCHAIN4J_CHAT_MODEL_API_FACTORY_BEAN_COUNT = 6;

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
        .run(
            context -> {
              assertHasAllBeansOf(context, ALL_BEANS);
              assertThat(context.getBeansOfType(Langchain4JChatModelApiFactory.class))
                  .hasSize(LANGCHAIN4J_CHAT_MODEL_API_FACTORY_BEAN_COUNT);
            });
  }

  @Test
  void whenAgenticAiConfigurationDisabled_thenNoAgenticConnectorBeansAreCreated() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.enabled=false")
        .run(
            context -> {
              assertDoesNotHaveAnyBeansOf(context, ALL_BEANS);
              assertThat(context.getBeansOfType(Langchain4JChatModelApiFactory.class)).isEmpty();
            });
  }

  @Test
  void whenAiAgentConnectorDisabled_thenNoAiAgentFunctionIsCreated() {
    // AiAgentTaskV2Function depends on the same OutboundConnectorAgentRequestHandler bean as v1,
    // so disabling the v1 flavor toggle also prevents the v2 connector bean from being created.
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.aiagent.outbound-connector.enabled=false")
        .run(
            context -> {
              assertHasAllBeansOf(
                  context,
                  ALL_BEANS.stream()
                      .filter(
                          notAnyOf(
                              OutboundConnectorAgentRequestHandler.class,
                              AiAgentFunction.class,
                              AiAgentTaskV2Function.class))
                      .toList());
              assertThat(context)
                  .doesNotHaveBean(OutboundConnectorAgentRequestHandler.class)
                  .doesNotHaveBean(AiAgentFunction.class)
                  .doesNotHaveBean(AiAgentTaskV2Function.class);
            });
  }

  @Test
  void whenAiAgentJobWorkerConnectorDisabled_thenNoAiAgentJobWorkerIsCreated() {
    // AiAgentSubProcessV2Function depends on the same JobWorkerAgentRequestHandler bean as v1, so
    // disabling the v1 flavor toggle also prevents the v2 connector bean from being created.
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.aiagent.job-worker.enabled=false")
        .run(
            context -> {
              assertHasAllBeansOf(
                  context,
                  ALL_BEANS.stream()
                      .filter(
                          notAnyOf(
                              JobWorkerAgentRequestHandler.class,
                              AiAgentJobWorker.class,
                              AiAgentSubProcessV2Function.class))
                      .toList());
              assertThat(context)
                  .doesNotHaveBean(JobWorkerAgentRequestHandler.class)
                  .doesNotHaveBean(AiAgentJobWorker.class)
                  .doesNotHaveBean(AiAgentSubProcessV2Function.class);
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
  class CustomChatModelApiFactoryOverrides {

    @ParameterizedTest
    @MethodSource("factoryOverrideCases")
    void userProvidedFactoryBeanOverridesDefault(FactoryOverrideCase override) {
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

    static Stream<FactoryOverrideCase> factoryOverrideCases() {
      return Stream.of(
          new FactoryOverrideCase(
              CustomAnthropicProviderConfig.class,
              "customAnthropicChatModelApiFactory",
              Langchain4JAnthropicChatModelApiFactory.class,
              CustomAnthropicChatModelApiFactory.class),
          new FactoryOverrideCase(
              CustomAzureOpenAiProviderConfig.class,
              "customAzureOpenAiChatModelApiFactory",
              Langchain4JAzureOpenAiChatModelApiFactory.class,
              CustomAzureOpenAiChatModelApiFactory.class),
          new FactoryOverrideCase(
              CustomBedrockProviderConfig.class,
              "customBedrockChatModelApiFactory",
              Langchain4JBedrockChatModelApiFactory.class,
              CustomBedrockChatModelApiFactory.class),
          new FactoryOverrideCase(
              CustomGoogleVertexAiProviderConfig.class,
              "customGoogleVertexAiChatModelApiFactory",
              Langchain4JGoogleVertexAiChatModelApiFactory.class,
              CustomGoogleVertexAiChatModelApiFactory.class),
          new FactoryOverrideCase(
              CustomOpenAiProviderConfig.class,
              "customOpenAiChatModelApiFactory",
              Langchain4JOpenAiChatModelApiFactory.class,
              CustomOpenAiChatModelApiFactory.class),
          new FactoryOverrideCase(
              CustomOpenAiCompatibleProviderConfig.class,
              "customOpenAiCompatibleChatModelApiFactory",
              Langchain4JOpenAiCompatibleChatModelApiFactory.class,
              CustomOpenAiCompatibleChatModelApiFactory.class));
    }

    record FactoryOverrideCase(
        Class<?> configurationClass,
        String beanName,
        Class<? extends Langchain4JChatModelApiFactory<?>> factoryClass,
        Class<? extends Langchain4JChatModelApiFactory<?>> customFactoryClass) {

      @Override
      public String toString() {
        return factoryClass.getSimpleName();
      }
    }

    static class CustomAnthropicProviderConfig {
      @Bean
      Langchain4JAnthropicChatModelApiFactory customAnthropicChatModelApiFactory() {
        return new CustomAnthropicChatModelApiFactory();
      }

      static class CustomAnthropicChatModelApiFactory
          extends Langchain4JAnthropicChatModelApiFactory {

        CustomAnthropicChatModelApiFactory() {
          super(
              mock(AgenticAiConnectorsConfigurationProperties.ChatModelProperties.class),
              mock(ChatModelHttpProxySupport.class),
              mock(ChatMessageConverter.class),
              mock(ToolSpecificationConverter.class),
              mock(JsonSchemaConverter.class),
              Langchain4JChatModelApi.DEFAULT_CAPABILITIES);
        }

        @Override
        protected CloseableChatModel createChatModel(
            AnthropicProviderConfiguration providerConfiguration) {
          return mock(CloseableChatModel.class);
        }
      }
    }

    static class CustomAzureOpenAiProviderConfig {
      @Bean
      Langchain4JAzureOpenAiChatModelApiFactory customAzureOpenAiChatModelApiFactory() {
        return new CustomAzureOpenAiChatModelApiFactory();
      }

      static class CustomAzureOpenAiChatModelApiFactory
          extends Langchain4JAzureOpenAiChatModelApiFactory {

        CustomAzureOpenAiChatModelApiFactory() {
          super(
              mock(AgenticAiConnectorsConfigurationProperties.ChatModelProperties.class),
              mock(ChatModelHttpProxySupport.class),
              mock(ChatMessageConverter.class),
              mock(ToolSpecificationConverter.class),
              mock(JsonSchemaConverter.class),
              Langchain4JChatModelApi.DEFAULT_CAPABILITIES);
        }

        @Override
        protected CloseableChatModel createChatModel(
            AzureOpenAiProviderConfiguration providerConfiguration) {
          return mock(CloseableChatModel.class);
        }
      }
    }

    static class CustomBedrockProviderConfig {
      @Bean
      Langchain4JBedrockChatModelApiFactory customBedrockChatModelApiFactory() {
        return new CustomBedrockChatModelApiFactory();
      }

      static class CustomBedrockChatModelApiFactory extends Langchain4JBedrockChatModelApiFactory {

        CustomBedrockChatModelApiFactory() {
          super(
              mock(AgenticAiConnectorsConfigurationProperties.ChatModelProperties.class),
              mock(ChatModelHttpProxySupport.class),
              mock(ChatMessageConverter.class),
              mock(ToolSpecificationConverter.class),
              mock(JsonSchemaConverter.class),
              Langchain4JChatModelApi.DEFAULT_CAPABILITIES);
        }

        @Override
        protected CloseableChatModel createChatModel(
            BedrockProviderConfiguration providerConfiguration) {
          return mock(CloseableChatModel.class);
        }
      }
    }

    static class CustomGoogleVertexAiProviderConfig {
      @Bean
      Langchain4JGoogleVertexAiChatModelApiFactory customGoogleVertexAiChatModelApiFactory() {
        return new CustomGoogleVertexAiChatModelApiFactory();
      }

      static class CustomGoogleVertexAiChatModelApiFactory
          extends Langchain4JGoogleVertexAiChatModelApiFactory {

        CustomGoogleVertexAiChatModelApiFactory() {
          super(
              mock(ChatMessageConverter.class),
              mock(ToolSpecificationConverter.class),
              mock(JsonSchemaConverter.class),
              Langchain4JChatModelApi.DEFAULT_CAPABILITIES);
        }

        @Override
        protected CloseableChatModel createChatModel(
            GoogleVertexAiProviderConfiguration providerConfiguration) {
          return mock(CloseableChatModel.class);
        }
      }
    }

    static class CustomOpenAiProviderConfig {
      @Bean
      Langchain4JOpenAiChatModelApiFactory customOpenAiChatModelApiFactory() {
        return new CustomOpenAiChatModelApiFactory();
      }

      static class CustomOpenAiChatModelApiFactory extends Langchain4JOpenAiChatModelApiFactory {

        CustomOpenAiChatModelApiFactory() {
          super(
              mock(AgenticAiConnectorsConfigurationProperties.ChatModelProperties.class),
              mock(ChatModelHttpProxySupport.class),
              mock(ChatMessageConverter.class),
              mock(ToolSpecificationConverter.class),
              mock(JsonSchemaConverter.class),
              Langchain4JChatModelApi.DEFAULT_CAPABILITIES);
        }

        @Override
        protected CloseableChatModel createChatModel(
            OpenAiProviderConfiguration providerConfiguration) {
          return mock(CloseableChatModel.class);
        }
      }
    }

    static class CustomOpenAiCompatibleProviderConfig {
      @Bean
      Langchain4JOpenAiCompatibleChatModelApiFactory customOpenAiCompatibleChatModelApiFactory() {
        return new CustomOpenAiCompatibleChatModelApiFactory();
      }

      static class CustomOpenAiCompatibleChatModelApiFactory
          extends Langchain4JOpenAiCompatibleChatModelApiFactory {

        CustomOpenAiCompatibleChatModelApiFactory() {
          super(
              mock(AgenticAiConnectorsConfigurationProperties.ChatModelProperties.class),
              mock(ChatModelHttpProxySupport.class),
              mock(ChatMessageConverter.class),
              mock(ToolSpecificationConverter.class),
              mock(JsonSchemaConverter.class),
              Langchain4JChatModelApi.DEFAULT_CAPABILITIES);
        }

        @Override
        protected CloseableChatModel createChatModel(
            OpenAiCompatibleProviderConfiguration providerConfiguration) {
          return mock(CloseableChatModel.class);
        }
      }
    }
  }

  private Predicate<Class<?>> notAnyOf(Class<?>... classes) {
    return c -> Stream.of(classes).noneMatch(c::equals);
  }
}
