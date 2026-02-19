/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.jobhandling.CommandExceptionHandlingStrategy;
import io.camunda.client.metrics.MetricsRecorder;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.AdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.aiagent.AiAgentFunction;
import io.camunda.connector.agenticai.aiagent.AiAgentJobWorker;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializer;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializerImpl;
import io.camunda.connector.agenticai.aiagent.agent.AgentLimitsValidator;
import io.camunda.connector.agenticai.aiagent.agent.AgentLimitsValidatorImpl;
import io.camunda.connector.agenticai.aiagent.agent.AgentMessagesHandler;
import io.camunda.connector.agenticai.aiagent.agent.AgentMessagesHandlerImpl;
import io.camunda.connector.agenticai.aiagent.agent.AgentResponseHandler;
import io.camunda.connector.agenticai.aiagent.agent.AgentResponseHandlerImpl;
import io.camunda.connector.agenticai.aiagent.agent.AgentToolsResolver;
import io.camunda.connector.agenticai.aiagent.agent.AgentToolsResolverImpl;
import io.camunda.connector.agenticai.aiagent.agent.JobWorkerAgentRequestHandler;
import io.camunda.connector.agenticai.aiagent.agent.OutboundConnectorAgentRequestHandler;
import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkAdapter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.configuration.AgenticAiLangchain4JFrameworkConfiguration;
import io.camunda.connector.agenticai.aiagent.jobworker.AiAgentJobWorkerHandler;
import io.camunda.connector.agenticai.aiagent.jobworker.AiAgentJobWorkerHandlerImpl;
import io.camunda.connector.agenticai.aiagent.jobworker.AiAgentJobWorkerValueCustomizer;
import io.camunda.connector.agenticai.aiagent.jobworker.JobWorkerAgentExecutionContextFactory;
import io.camunda.connector.agenticai.aiagent.jobworker.JobWorkerAgentExecutionContextFactoryImpl;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationContextModule;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistryImpl;
import io.camunda.connector.agenticai.aiagent.memory.conversation.document.CamundaDocumentConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationStore;
import io.camunda.connector.agenticai.aiagent.systemprompt.SystemPromptComposer;
import io.camunda.connector.agenticai.aiagent.systemprompt.SystemPromptComposerImpl;
import io.camunda.connector.agenticai.aiagent.systemprompt.SystemPromptContributor;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import io.camunda.connector.runtime.core.ConnectorResultHandler;
import io.camunda.connector.runtime.core.document.store.CamundaDocumentStore;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.core.validation.ValidationUtil;
import io.camunda.connector.runtime.outbound.job.OutboundConnectorExceptionHandler;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

@Configuration
@ConditionalOnBooleanProperty(value = "camunda.connector.agenticai.enabled", matchIfMissing = true)
@EnableConfigurationProperties(AgenticAiConnectorsConfigurationProperties.class)
@Import({AgenticAiLangchain4JFrameworkConfiguration.class})
public class AgenticAiConnectorsAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public ConversationContextModule conversationContextModule() {
    return new ConversationContextModule();
  }

  /**
   * Registers {@link ConversationContextModule} on all {@link ObjectMapper} beans in the context.
   * This is necessary because the connector runtime creates its own ObjectMapper instances (via
   * {@code ConnectorsObjectMapperSupplier.getCopy()}) that don't pick up Spring-managed Jackson
   * modules automatically.
   */
  @Bean
  static BeanPostProcessor conversationContextObjectMapperPostProcessor() {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof ObjectMapper om) {
          om.registerModule(new ConversationContextModule());
        }
        return bean;
      }
    };
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentToolsResolver aiAgentToolsResolver(
      AdHocToolsSchemaResolver toolsSchemaResolver,
      GatewayToolHandlerRegistry gatewayToolHandlers) {
    return new AgentToolsResolverImpl(toolsSchemaResolver, gatewayToolHandlers);
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentInitializer aiAgentInitializer(
      AgentToolsResolver toolsResolver, GatewayToolHandlerRegistry gatewayToolHandlers) {
    return new AgentInitializerImpl(toolsResolver, gatewayToolHandlers);
  }

  @Bean
  @ConditionalOnMissingBean
  public InProcessConversationStore aiAgentInProcessConversationStore() {
    return new InProcessConversationStore();
  }

  @Bean
  @ConditionalOnMissingBean
  public CamundaDocumentConversationStore aiAgentCamundaDocumentConversationStore(
      DocumentFactory documentFactory,
      CamundaDocumentStore documentStore,
      @ConnectorsObjectMapper ObjectMapper objectMapper) {
    return new CamundaDocumentConversationStore(documentFactory, documentStore, objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public ConversationStoreRegistry aiAgentConversationStoreRegistry(
      List<ConversationStore> conversationStores) {
    return new ConversationStoreRegistryImpl(conversationStores);
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentLimitsValidator aiAgentLimitsValidator() {
    return new AgentLimitsValidatorImpl();
  }

  @Bean
  @ConditionalOnMissingBean
  public SystemPromptComposer aiAgentSystemPromptComposer(
      List<SystemPromptContributor> contributors) {
    return new SystemPromptComposerImpl(contributors);
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentMessagesHandler aiAgentMessagesHandler(
      GatewayToolHandlerRegistry gatewayToolHandlers, SystemPromptComposer systemPromptComposer) {
    return new AgentMessagesHandlerImpl(gatewayToolHandlers, systemPromptComposer);
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentResponseHandler aiAgentResponseHandler(
      @ConnectorsObjectMapper ObjectMapper objectMapper) {
    return new AgentResponseHandlerImpl(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBooleanProperty(
      value = "camunda.connector.agenticai.aiagent.outbound-connector.enabled",
      matchIfMissing = true)
  public OutboundConnectorAgentRequestHandler aiAgentOutboundConnectorAgentRequestHandler(
      AgentInitializer agentInitializer,
      ConversationStoreRegistry conversationStoreRegistry,
      AgentLimitsValidator limitsValidator,
      AgentMessagesHandler messagesHandler,
      GatewayToolHandlerRegistry gatewayToolHandlers,
      AiFrameworkAdapter<?> aiFrameworkAdapter,
      AgentResponseHandler responseHandler) {
    return new OutboundConnectorAgentRequestHandler(
        agentInitializer,
        conversationStoreRegistry,
        limitsValidator,
        messagesHandler,
        gatewayToolHandlers,
        aiFrameworkAdapter,
        responseHandler);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBooleanProperty(
      value = "camunda.connector.agenticai.aiagent.outbound-connector.enabled",
      matchIfMissing = true)
  public AiAgentFunction aiAgentFunction(
      ProcessDefinitionAdHocToolElementsResolver toolElementsResolver,
      OutboundConnectorAgentRequestHandler agentRequestHandler) {
    return new AiAgentFunction(toolElementsResolver, agentRequestHandler);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBooleanProperty(
      value = "camunda.connector.agenticai.aiagent.job-worker.enabled",
      matchIfMissing = true)
  public AiAgentJobWorkerValueCustomizer aiAgentJobWorkerValueCustomizer(Environment environment) {
    return new AiAgentJobWorkerValueCustomizer(environment);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBooleanProperty(
      value = "camunda.connector.agenticai.aiagent.job-worker.enabled",
      matchIfMissing = true)
  public JobWorkerAgentRequestHandler aiAgentJobWorkerAgentRequestHandler(
      AgentInitializer agentInitializer,
      ConversationStoreRegistry conversationStoreRegistry,
      AgentLimitsValidator limitsValidator,
      AgentMessagesHandler messagesHandler,
      GatewayToolHandlerRegistry gatewayToolHandlers,
      AiFrameworkAdapter<?> aiFrameworkAdapter,
      AgentResponseHandler responseHandler) {
    return new JobWorkerAgentRequestHandler(
        agentInitializer,
        conversationStoreRegistry,
        limitsValidator,
        messagesHandler,
        gatewayToolHandlers,
        aiFrameworkAdapter,
        responseHandler);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBooleanProperty(
      value = "camunda.connector.agenticai.aiagent.job-worker.enabled",
      matchIfMissing = true)
  public JobWorkerAgentExecutionContextFactory aiAgentJobWorkerAgentExecutionContextFactory(
      SecretProviderAggregator secretProvider,
      @Autowired(required = false) ValidationProvider validationProvider,
      DocumentFactory documentFactory,
      @ConnectorsObjectMapper ObjectMapper objectMapper) {
    if (validationProvider == null) {
      validationProvider = ValidationUtil.discoverDefaultValidationProviderImplementation();
    }

    return new JobWorkerAgentExecutionContextFactoryImpl(
        secretProvider, validationProvider, documentFactory, objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBooleanProperty(
      value = "camunda.connector.agenticai.aiagent.job-worker.enabled",
      matchIfMissing = true)
  public AiAgentJobWorkerHandler aiAgentJobWorkerHandler(
      JobWorkerAgentExecutionContextFactory executionContextFactory,
      JobWorkerAgentRequestHandler agentRequestHandler,
      CommandExceptionHandlingStrategy exceptionHandlingStrategy,
      SecretProviderAggregator secretProvider,
      @ConnectorsObjectMapper ObjectMapper objectMapper,
      MetricsRecorder metricsRecorder) {
    return new AiAgentJobWorkerHandlerImpl(
        executionContextFactory,
        agentRequestHandler,
        exceptionHandlingStrategy,
        new OutboundConnectorExceptionHandler(secretProvider),
        new ConnectorResultHandler(objectMapper),
        metricsRecorder);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBooleanProperty(
      value = "camunda.connector.agenticai.aiagent.job-worker.enabled",
      matchIfMissing = true)
  public AiAgentJobWorker aiAgentJobWorker(AiAgentJobWorkerHandler jobWorkerHandler) {
    return new AiAgentJobWorker(jobWorkerHandler);
  }
}
