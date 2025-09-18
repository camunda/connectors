/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.jobhandling.CommandExceptionHandlingStrategy;
import io.camunda.connector.agenticai.adhoctoolsschema.AdHocToolsSchemaFunction;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.CachingProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.CamundaClientProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.ProcessDefinitionClient;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.feel.AdHocToolElementParameterExtractor;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.feel.AdHocToolElementParameterExtractorImpl;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.AdHocToolSchemaGenerator;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.AdHocToolSchemaGeneratorImpl;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.AdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.AdHocToolsSchemaResolverImpl;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.GatewayToolDefinitionResolver;
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
import io.camunda.connector.agenticai.aiagent.agent.JobWorkerAgentRequestHandler;
import io.camunda.connector.agenticai.aiagent.agent.OutboundConnectorAgentRequestHandler;
import io.camunda.connector.agenticai.aiagent.framework.AiFrameworkAdapter;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.configuration.AgenticAiLangchain4JFrameworkConfiguration;
import io.camunda.connector.agenticai.aiagent.jobworker.AiAgentJobWorkerHandler;
import io.camunda.connector.agenticai.aiagent.jobworker.AiAgentJobWorkerHandlerImpl;
import io.camunda.connector.agenticai.aiagent.jobworker.AiAgentJobWorkerValueCustomizer;
import io.camunda.connector.agenticai.aiagent.jobworker.JobWorkerAgentExecutionContextFactory;
import io.camunda.connector.agenticai.aiagent.jobworker.JobWorkerAgentExecutionContextFactoryImpl;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistryImpl;
import io.camunda.connector.agenticai.aiagent.memory.conversation.document.CamundaDocumentConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationStore;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandler;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistryImpl;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfiguration;
import io.camunda.connector.agenticai.mcp.client.configuration.McpRemoteClientConfiguration;
import io.camunda.connector.agenticai.mcp.discovery.configuration.McpDiscoveryConfiguration;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.ConnectorResultHandler;
import io.camunda.connector.runtime.core.document.store.CamundaDocumentStore;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.core.validation.ValidationUtil;
import io.camunda.connector.runtime.metrics.ConnectorsOutboundMetrics;
import io.camunda.connector.runtime.outbound.job.OutboundConnectorExceptionHandler;
import io.camunda.zeebe.feel.tagged.impl.TaggedParameterExtractor;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
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
@Import({
  AgenticAiLangchain4JFrameworkConfiguration.class,
  McpDiscoveryConfiguration.class,
  McpClientConfiguration.class,
  McpRemoteClientConfiguration.class
})
public class AgenticAiConnectorsAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AdHocToolElementParameterExtractor aiAgentAdHocToolElementParameterExtractor() {
    return new AdHocToolElementParameterExtractorImpl(new TaggedParameterExtractor());
  }

  @Bean
  @ConditionalOnMissingBean
  public AdHocToolSchemaGenerator aiAgentAdHocToolSchemaGenerator() {
    return new AdHocToolSchemaGeneratorImpl();
  }

  @Bean
  @ConditionalOnMissingBean
  public AdHocToolsSchemaResolver aiAgentAdHocToolDefinitionResolver(
      List<GatewayToolDefinitionResolver> gatewayToolDefinitionResolvers,
      AdHocToolSchemaGenerator schemaGenerator) {
    return new AdHocToolsSchemaResolverImpl(gatewayToolDefinitionResolvers, schemaGenerator);
  }

  @Bean
  @ConditionalOnMissingBean
  public ProcessDefinitionAdHocToolElementsResolver aiAgentProcessDefinitionToolElementsResolver(
      AgenticAiConnectorsConfigurationProperties configuration,
      CamundaClient camundaClient,
      AdHocToolElementParameterExtractor parameterExtractor) {
    final var processDefinitionClient =
        new ProcessDefinitionClient(
            camundaClient, configuration.tools().processDefinition().retries());
    final var resolver =
        new CamundaClientProcessDefinitionAdHocToolElementsResolver(
            processDefinitionClient, parameterExtractor);

    final var cacheConfiguration = configuration.tools().processDefinition().cache();
    if (cacheConfiguration.enabled()) {
      return new CachingProcessDefinitionAdHocToolElementsResolver(
          resolver,
          new CachingProcessDefinitionAdHocToolElementsResolver.CacheConfiguration(
              cacheConfiguration.maximumSize(), cacheConfiguration.expireAfterWrite()));
    }

    return resolver;
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBooleanProperty(
      value = "camunda.connector.agenticai.ad-hoc-tools-schema-resolver.enabled",
      matchIfMissing = true)
  public AdHocToolsSchemaFunction aiAgentAdHocToolsSchemaFunction(
      ProcessDefinitionAdHocToolElementsResolver toolElementsResolver,
      AdHocToolsSchemaResolver toolsSchemaResolver) {
    return new AdHocToolsSchemaFunction(toolElementsResolver, toolsSchemaResolver);
  }

  @Bean
  @ConditionalOnMissingBean
  public GatewayToolHandlerRegistry aiAgentGatewayToolHandlerRegistry(
      List<GatewayToolHandler> gatewayToolHandlers) {
    return new GatewayToolHandlerRegistryImpl(gatewayToolHandlers);
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentInitializer aiAgentInitializer(
      AdHocToolsSchemaResolver toolsSchemaResolver,
      GatewayToolHandlerRegistry gatewayToolHandlers) {
    return new AgentInitializerImpl(toolsSchemaResolver, gatewayToolHandlers);
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
      ObjectMapper objectMapper) {
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
  public AgentMessagesHandler aiAgentMessagesHandler(
      GatewayToolHandlerRegistry gatewayToolHandlers) {
    return new AgentMessagesHandlerImpl(gatewayToolHandlers);
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentResponseHandler aiAgentResponseHandler(ObjectMapper objectMapper) {
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
      ObjectMapper objectMapper) {
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
      ObjectMapper objectMapper,
      ConnectorsOutboundMetrics connectorsOutboundMetrics) {
    return new AiAgentJobWorkerHandlerImpl(
        executionContextFactory,
        agentRequestHandler,
        exceptionHandlingStrategy,
        new OutboundConnectorExceptionHandler(secretProvider),
        new ConnectorResultHandler(objectMapper),
        connectorsOutboundMetrics);
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
