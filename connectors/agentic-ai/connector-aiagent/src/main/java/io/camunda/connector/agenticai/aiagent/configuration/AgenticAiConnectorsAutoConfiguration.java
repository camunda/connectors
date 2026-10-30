/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.AgentSubProcessV1Function;
import io.camunda.connector.agenticai.aiagent.AgentSubProcessV2Function;
import io.camunda.connector.agenticai.aiagent.AgentTaskV1Function;
import io.camunda.connector.agenticai.aiagent.AgentTaskV2Function;
import io.camunda.connector.agenticai.aiagent.agent.AgentConversationTurnInputComposer;
import io.camunda.connector.agenticai.aiagent.agent.AgentConversationTurnInputComposerImpl;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializer;
import io.camunda.connector.agenticai.aiagent.agent.AgentInitializerImpl;
import io.camunda.connector.agenticai.aiagent.agent.AgentResponseHandler;
import io.camunda.connector.agenticai.aiagent.agent.AgentResponseHandlerImpl;
import io.camunda.connector.agenticai.aiagent.agent.AgentSubProcessRequestHandler;
import io.camunda.connector.agenticai.aiagent.agent.AgentTaskRequestHandler;
import io.camunda.connector.agenticai.aiagent.agent.AgentToolsResolver;
import io.camunda.connector.agenticai.aiagent.agent.AgentToolsResolverImpl;
import io.camunda.connector.agenticai.aiagent.agent.ResponseSchemaValidator;
import io.camunda.connector.agenticai.aiagent.agent.RuntimeToolExecutor;
import io.camunda.connector.agenticai.aiagent.agent.RuntimeToolExecutorImpl;
import io.camunda.connector.agenticai.aiagent.agent.ToolCallResultCompletedAtResolver;
import io.camunda.connector.agenticai.aiagent.agent.ToolCallResultDocumentExtractor;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceClient;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceHistoryMapper;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceToolMapper;
import io.camunda.connector.agenticai.aiagent.agentinstance.CamundaAgentInstanceClient;
import io.camunda.connector.agenticai.aiagent.api.chatmodel.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.api.conversation.ConversationStore;
import io.camunda.connector.agenticai.aiagent.api.systemprompt.SystemPromptContributor;
import io.camunda.connector.agenticai.aiagent.api.tool.GatewayToolHandler;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeTool;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRegistry;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelRegistryImpl;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.configuration.AgenticAiNativeProvidersConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.langchain4j.configuration.AgenticAiLangChain4JFrameworkConfiguration;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistry;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRegistryImpl;
import io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.AwsAgentCoreConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.DefaultBedrockAgentCoreClientFactory;
import io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.mapping.AwsAgentCoreConversationMapper;
import io.camunda.connector.agenticai.aiagent.memory.conversation.document.CamundaDocumentConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationStore;
import io.camunda.connector.agenticai.aiagent.proxy.aws.AwsSdkHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.proxy.azure.AzureHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.request.V1ToV2ProviderConfigurationMapper;
import io.camunda.connector.agenticai.aiagent.request.V1ToV2ProviderConfigurationMapperImpl;
import io.camunda.connector.agenticai.aiagent.systemprompt.SystemPromptComposer;
import io.camunda.connector.agenticai.aiagent.systemprompt.SystemPromptComposerImpl;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistryImpl;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolRegistry;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolRegistryImpl;
import io.camunda.connector.agenticai.common.configuration.AgenticAiCommonAutoConfiguration;
import io.camunda.connector.agenticai.common.http.AgenticAiHttpProxySupport;
import io.camunda.connector.agenticai.common.retry.CamundaApiRetry.Sleeper;
import io.camunda.connector.agenticai.tools.schema.AdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.tools.schema.configuration.AgenticAiToolsSchemaAutoConfiguration;
import io.camunda.connector.agenticai.tools.schema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import io.camunda.connector.runtime.core.document.store.CamundaDocumentStore;
import io.camunda.connector.runtime.tenant.PhysicalTenantClientSelector;
import java.util.List;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;

@AutoConfiguration(
    after = {AgenticAiCommonAutoConfiguration.class, AgenticAiToolsSchemaAutoConfiguration.class})
@ConditionalOnBooleanProperty(value = "camunda.connector.agenticai.enabled", matchIfMissing = true)
@EnableConfigurationProperties(AgenticAiConnectorsConfigurationProperties.class)
@Import({
  AgenticAiLangChain4JFrameworkConfiguration.class,
  AgenticAiNativeProvidersConfiguration.class
})
public class AgenticAiConnectorsAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public GatewayToolHandlerRegistry aiAgentGatewayToolHandlerRegistry(
      List<GatewayToolHandler> gatewayToolHandlers) {
    return new GatewayToolHandlerRegistryImpl(gatewayToolHandlers);
  }

  @Bean
  @ConditionalOnMissingBean
  public RuntimeToolRegistry aiAgentRuntimeToolRegistry(List<RuntimeTool> runtimeTools) {
    return new RuntimeToolRegistryImpl(runtimeTools);
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentToolsResolver aiAgentToolsResolver(
      AdHocToolsSchemaResolver toolsSchemaResolver,
      GatewayToolHandlerRegistry gatewayToolHandlers,
      RuntimeToolRegistry runtimeToolRegistry) {
    return new AgentToolsResolverImpl(
        toolsSchemaResolver, gatewayToolHandlers, runtimeToolRegistry);
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentInstanceHistoryMapper aiAgentInstanceHistoryMapper(
      GatewayToolHandlerRegistry gatewayToolHandlers) {
    return new AgentInstanceHistoryMapper(gatewayToolHandlers);
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentInstanceToolMapper aiAgentInstanceToolMapper(
      GatewayToolHandlerRegistry gatewayToolHandlers, RuntimeToolRegistry runtimeToolRegistry) {
    return new AgentInstanceToolMapper(gatewayToolHandlers, runtimeToolRegistry);
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentInstanceClient aiAgentInstanceClient(
      PhysicalTenantClientSelector clientSelector,
      AgenticAiConnectorsConfigurationProperties configuration,
      AgentInstanceHistoryMapper historyMapper,
      AgentInstanceToolMapper toolMapper) {
    return new CamundaAgentInstanceClient(
        clientSelector,
        configuration.aiagent().agentInstance().retries(),
        Sleeper.threadSleep(),
        historyMapper,
        toolMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentInitializer aiAgentInitializer(
      AgentToolsResolver toolsResolver,
      GatewayToolHandlerRegistry gatewayToolHandlers,
      AgentInstanceClient agentInstanceClient) {
    return new AgentInitializerImpl(
        toolsResolver,
        gatewayToolHandlers,
        agentInstanceClient,
        new ToolCallResultCompletedAtResolver());
  }

  @Bean
  @ConditionalOnMissingBean
  public ResponseSchemaValidator aiAgentResponseSchemaValidator(
      @ConnectorsObjectMapper ObjectMapper objectMapper,
      AgenticAiConnectorsConfigurationProperties configuration) {
    final var limits = configuration.aiagent().responseSchema();
    return new ResponseSchemaValidator(
        objectMapper, limits.maxSizeBytes(), limits.maxNestingDepth());
  }

  @Bean
  @ConditionalOnMissingBean
  public AwsSdkHttpProxySupport aiAgentAwsSdkHttpProxySupport(
      AgenticAiHttpProxySupport httpProxySupport) {
    return new AwsSdkHttpProxySupport(httpProxySupport.getProxyConfiguration());
  }

  @Bean
  @ConditionalOnMissingBean
  public AzureHttpProxySupport aiAgentAzureHttpProxySupport(
      AgenticAiHttpProxySupport httpProxySupport) {
    return new AzureHttpProxySupport(httpProxySupport.getProxyConfiguration());
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
      PhysicalTenantClientSelector clientSelector,
      @ConnectorsObjectMapper ObjectMapper objectMapper) {
    return new CamundaDocumentConversationStore(
        documentFactory, documentStore, clientSelector, objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public AwsAgentCoreConversationMapper aiAgentAwsAgentCoreConversationMapper(
      @ConnectorsObjectMapper ObjectMapper objectMapper) {
    return new AwsAgentCoreConversationMapper(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public AwsAgentCoreConversationStore aiAgentAwsAgentCoreConversationStore(
      AwsAgentCoreConversationMapper conversationMapper,
      AwsSdkHttpProxySupport awsSdkHttpProxySupport) {
    return new AwsAgentCoreConversationStore(
        new DefaultBedrockAgentCoreClientFactory(awsSdkHttpProxySupport), conversationMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public ConversationStoreRegistry aiAgentConversationStoreRegistry(
      List<ConversationStore> conversationStores) {
    return new ConversationStoreRegistryImpl(conversationStores);
  }

  @Bean
  @ConditionalOnMissingBean
  public SystemPromptComposer aiAgentSystemPromptComposer(
      List<SystemPromptContributor> contributors) {
    return new SystemPromptComposerImpl(contributors);
  }

  @Bean
  @ConditionalOnMissingBean
  public ToolCallResultDocumentExtractor aiAgentToolCallResultDocumentExtractor(
      GatewayToolHandlerRegistry gatewayToolHandlers) {
    return new ToolCallResultDocumentExtractor(gatewayToolHandlers);
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentConversationTurnInputComposer aiAgentConversationTurnInputComposer(
      GatewayToolHandlerRegistry gatewayToolHandlers) {
    return new AgentConversationTurnInputComposerImpl(gatewayToolHandlers);
  }

  @Bean
  @ConditionalOnMissingBean
  public RuntimeToolExecutor aiAgentRuntimeToolExecutor(
      RuntimeToolRegistry runtimeToolRegistry, @ConnectorsObjectMapper ObjectMapper objectMapper) {
    return new RuntimeToolExecutorImpl(runtimeToolRegistry, objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public AgentResponseHandler aiAgentResponseHandler(
      @ConnectorsObjectMapper ObjectMapper objectMapper,
      GatewayToolHandlerRegistry gatewayToolHandlers) {
    return new AgentResponseHandlerImpl(objectMapper, gatewayToolHandlers);
  }

  @Bean
  @ConditionalOnMissingBean
  public ChatModelRegistry aiAgentChatModelRegistry(List<ChatModelFactory> factories) {
    return new ChatModelRegistryImpl(factories);
  }

  @Bean
  @ConditionalOnMissingBean
  public V1ToV2ProviderConfigurationMapper aiAgentV1ToV2ProviderConfigurationMapper(
      AgenticAiConnectorsConfigurationProperties configuration) {
    return new V1ToV2ProviderConfigurationMapperImpl(configuration.aiagent().chatModel());
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBooleanProperty(
      value = "camunda.connector.agenticai.aiagent.outbound-connector.enabled",
      matchIfMissing = true)
  public AgentTaskRequestHandler aiAgentTaskRequestHandler(
      AgentInitializer agentInitializer,
      ConversationStoreRegistry conversationStoreRegistry,
      AgentConversationTurnInputComposer agentConversationTurnInputComposer,
      ChatModelRegistry chatModelRegistry,
      SystemPromptComposer systemPromptComposer,
      AgentResponseHandler responseHandler,
      AgentInstanceClient agentInstanceClient,
      ResponseSchemaValidator responseSchemaValidator,
      RuntimeToolRegistry runtimeToolRegistry,
      RuntimeToolExecutor runtimeToolExecutor) {
    return new AgentTaskRequestHandler(
        agentInitializer,
        conversationStoreRegistry,
        agentConversationTurnInputComposer,
        chatModelRegistry,
        systemPromptComposer,
        responseHandler,
        agentInstanceClient,
        responseSchemaValidator,
        runtimeToolRegistry,
        runtimeToolExecutor);
  }

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @ConditionalOnMissingBean
  @ConditionalOnBooleanProperty(
      value = "camunda.connector.agenticai.aiagent.outbound-connector.enabled",
      matchIfMissing = true)
  @SuppressWarnings("deprecation")
  public AgentTaskV1Function aiAgentTaskV1Function(
      ProcessDefinitionAdHocToolElementsResolver toolElementsResolver,
      AgentTaskRequestHandler agentRequestHandler,
      V1ToV2ProviderConfigurationMapper v1ProviderConfigurationMapper,
      AgenticAiConnectorsConfigurationProperties configuration) {
    return new AgentTaskV1Function(
        toolElementsResolver,
        agentRequestHandler,
        v1ProviderConfigurationMapper,
        configuration.aiagent().rewriteV1ProviderConfigToV2());
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBooleanProperty(
      value = "camunda.connector.agenticai.aiagent.job-worker.enabled",
      matchIfMissing = true)
  public AgentSubProcessRequestHandler aiAgentSubProcessRequestHandler(
      AgentInitializer agentInitializer,
      ConversationStoreRegistry conversationStoreRegistry,
      AgentConversationTurnInputComposer agentConversationTurnInputComposer,
      ChatModelRegistry chatModelRegistry,
      SystemPromptComposer systemPromptComposer,
      AgentResponseHandler responseHandler,
      AgentInstanceClient agentInstanceClient,
      ResponseSchemaValidator responseSchemaValidator,
      RuntimeToolRegistry runtimeToolRegistry,
      RuntimeToolExecutor runtimeToolExecutor) {
    return new AgentSubProcessRequestHandler(
        agentInitializer,
        conversationStoreRegistry,
        agentConversationTurnInputComposer,
        chatModelRegistry,
        systemPromptComposer,
        responseHandler,
        agentInstanceClient,
        responseSchemaValidator,
        runtimeToolRegistry,
        runtimeToolExecutor);
  }

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @ConditionalOnMissingBean
  @ConditionalOnBooleanProperty(
      value = "camunda.connector.agenticai.aiagent.job-worker.enabled",
      matchIfMissing = true)
  @SuppressWarnings("deprecation")
  public AgentSubProcessV1Function aiAgentSubProcessV1Function(
      AgentSubProcessRequestHandler agentRequestHandler,
      V1ToV2ProviderConfigurationMapper v1ProviderConfigurationMapper,
      AgenticAiConnectorsConfigurationProperties configuration) {
    return new AgentSubProcessV1Function(
        agentRequestHandler,
        v1ProviderConfigurationMapper,
        configuration.aiagent().rewriteV1ProviderConfigToV2());
  }

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @ConditionalOnMissingBean
  @ConditionalOnBean(AgentTaskRequestHandler.class)
  @ConditionalOnBooleanProperty(
      value = "camunda.connector.agenticai.aiagent.outbound-connector.enabled",
      matchIfMissing = true)
  public AgentTaskV2Function aiAgentTaskV2Function(
      ProcessDefinitionAdHocToolElementsResolver toolElementsResolver,
      AgentTaskRequestHandler agentRequestHandler) {
    return new AgentTaskV2Function(toolElementsResolver, agentRequestHandler);
  }

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @ConditionalOnMissingBean
  @ConditionalOnBean(AgentSubProcessRequestHandler.class)
  @ConditionalOnBooleanProperty(
      value = "camunda.connector.agenticai.aiagent.job-worker.enabled",
      matchIfMissing = true)
  public AgentSubProcessV2Function aiAgentSubProcessV2Function(
      AgentSubProcessRequestHandler agentRequestHandler) {
    return new AgentSubProcessV2Function(agentRequestHandler);
  }
}
