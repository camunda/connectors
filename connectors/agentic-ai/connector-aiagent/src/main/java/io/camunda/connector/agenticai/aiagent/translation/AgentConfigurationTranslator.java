/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.translation;

import io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.AwsAgentCoreConversationStoreConfiguration;
import io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.AwsAgentCoreConversationStoreConfiguration.Authentication.DefaultCredentialsChain;
import io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.AwsAgentCoreConversationStoreConfiguration.Authentication.StaticCredentials;
import io.camunda.connector.agenticai.aiagent.memory.conversation.document.CamundaDocumentConversationStoreConfiguration;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationStoreConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ConversationStoreConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.CustomConversationStoreConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.EventHandlingConfiguration.EventHandlingBehavior;
import io.camunda.connector.agenticai.aiagent.model.request.LimitsConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.JsonResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.ResponseFormatConfiguration.TextResponseFormatConfiguration;
import io.camunda.connector.agenticai.aiagent.request.AgentSubProcessRequestData;
import io.camunda.connector.agenticai.aiagent.request.AgentSubProcessResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.request.AgentTaskRequestData;
import io.camunda.connector.agenticai.aiagent.request.AgentTaskResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.request.EventHandlingTemplateConfiguration;
import io.camunda.connector.agenticai.aiagent.request.LimitsTemplateConfiguration;
import io.camunda.connector.agenticai.aiagent.request.MemoryStorageTemplateConfiguration;
import io.camunda.connector.agenticai.aiagent.request.MemoryStorageTemplateConfiguration.AwsAgentCoreAuthentication.AwsDefaultCredentialsChainAuthentication;
import io.camunda.connector.agenticai.aiagent.request.MemoryStorageTemplateConfiguration.AwsAgentCoreAuthentication.AwsStaticCredentialsAuthentication;
import io.camunda.connector.agenticai.aiagent.request.MemoryStorageTemplateConfiguration.AwsAgentCoreMemoryStorageConfiguration;
import io.camunda.connector.agenticai.aiagent.request.MemoryStorageTemplateConfiguration.CamundaDocumentMemoryStorageConfiguration;
import io.camunda.connector.agenticai.aiagent.request.MemoryStorageTemplateConfiguration.CustomMemoryStorageConfiguration;
import io.camunda.connector.agenticai.aiagent.request.MemoryStorageTemplateConfiguration.InProcessMemoryStorageConfiguration;
import io.camunda.connector.agenticai.aiagent.request.MemoryTemplateConfiguration;
import io.camunda.connector.agenticai.aiagent.request.PromptTemplateConfiguration.SystemPromptTemplateConfiguration;
import io.camunda.connector.agenticai.aiagent.request.PromptTemplateConfiguration.UserPromptTemplateConfiguration;
import io.camunda.connector.agenticai.aiagent.request.ResponseFormatTemplateConfiguration;
import io.camunda.connector.agenticai.aiagent.request.ResponseFormatTemplateConfiguration.JsonResponseFormatTemplateConfiguration;
import io.camunda.connector.agenticai.aiagent.request.ResponseFormatTemplateConfiguration.TextResponseFormatTemplateConfiguration;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Translates the connector's request records into the non-null {@link AgentConfiguration} domain
 * model. Every default is applied here, once. A value which is absent in the request resolves to
 * the behavior the agent always had when the option was not set, which is not necessarily the
 * default the element template pre-fills.
 */
public final class AgentConfigurationTranslator {

  static final int DEFAULT_CONTEXT_WINDOW_SIZE = 20;
  static final int DEFAULT_MAX_MODEL_CALLS = 10;

  private AgentConfigurationTranslator() {}

  /** Translates the request of the AI Agent task (outbound connector), which has no events. */
  public static AgentConfiguration translate(
      ChatModelConfiguration chatModel, AgentTaskRequestData data) {
    return new AgentConfiguration(
        chatModel,
        systemPrompt(data.systemPrompt()),
        userPrompt(data.userPrompt()),
        memory(data.memory()),
        limits(data.limits()),
        events(null),
        response(data.response()));
  }

  /** Translates the request of the AI Agent sub-process. */
  public static AgentConfiguration translate(
      ChatModelConfiguration chatModel, AgentSubProcessRequestData data) {
    return new AgentConfiguration(
        chatModel,
        systemPrompt(data.systemPrompt()),
        userPrompt(data.userPrompt()),
        memory(data.memory()),
        limits(data.limits()),
        events(data.events()),
        response(data.response()));
  }

  private static SystemPromptConfiguration systemPrompt(SystemPromptTemplateConfiguration prompt) {
    return new SystemPromptConfiguration(prompt.prompt());
  }

  private static UserPromptConfiguration userPrompt(UserPromptTemplateConfiguration prompt) {
    return new UserPromptConfiguration(
        prompt.prompt(), prompt.documents() == null ? List.of() : prompt.documents());
  }

  private static MemoryConfiguration memory(@Nullable MemoryTemplateConfiguration memory) {
    if (memory == null) {
      return new MemoryConfiguration(
          new InProcessConversationStoreConfiguration(), DEFAULT_CONTEXT_WINDOW_SIZE);
    }
    return new MemoryConfiguration(
        storage(memory.storage()),
        memory.contextWindowSize() != null
            ? memory.contextWindowSize()
            : DEFAULT_CONTEXT_WINDOW_SIZE);
  }

  private static ConversationStoreConfiguration storage(
      @Nullable MemoryStorageTemplateConfiguration storage) {
    if (storage == null) {
      return new InProcessConversationStoreConfiguration();
    }
    return switch (storage) {
      case InProcessMemoryStorageConfiguration ignored ->
          new InProcessConversationStoreConfiguration();
      case CamundaDocumentMemoryStorageConfiguration document ->
          new CamundaDocumentConversationStoreConfiguration(
              document.timeToLive(),
              document.customProperties() != null ? document.customProperties() : Map.of());
      case AwsAgentCoreMemoryStorageConfiguration agentCore ->
          new AwsAgentCoreConversationStoreConfiguration(
              agentCore.region(),
              agentCore.endpoint(),
              switch (agentCore.authentication()) {
                case AwsStaticCredentialsAuthentication credentials ->
                    new StaticCredentials(credentials.accessKey(), credentials.secretKey());
                case AwsDefaultCredentialsChainAuthentication ignored ->
                    new DefaultCredentialsChain();
              },
              agentCore.memoryId(),
              agentCore.actorId());
      case CustomMemoryStorageConfiguration custom ->
          CustomConversationStoreConfiguration.of(custom.storeType(), custom.parameters());
    };
  }

  private static LimitsConfiguration limits(@Nullable LimitsTemplateConfiguration limits) {
    return new LimitsConfiguration(
        limits != null && limits.maxModelCalls() != null
            ? limits.maxModelCalls()
            : DEFAULT_MAX_MODEL_CALLS);
  }

  private static EventHandlingConfiguration events(
      @Nullable EventHandlingTemplateConfiguration events) {
    return new EventHandlingConfiguration(
        events != null && events.behavior() != null
            ? events.behavior()
            : EventHandlingBehavior.WAIT_FOR_TOOL_CALL_RESULTS);
  }

  private static ResponseConfiguration response(@Nullable AgentTaskResponseConfiguration response) {
    return response == null
        ? defaultResponse()
        : response(response.format(), response.includeAssistantMessage());
  }

  private static ResponseConfiguration response(
      @Nullable AgentSubProcessResponseConfiguration response) {
    return response == null
        ? defaultResponse()
        : response(response.format(), response.includeAssistantMessage());
  }

  private static ResponseConfiguration response(
      ResponseFormatTemplateConfiguration format, @Nullable Boolean includeAssistantMessage) {
    return new ResponseConfiguration(format(format), Boolean.TRUE.equals(includeAssistantMessage));
  }

  private static ResponseConfiguration defaultResponse() {
    return new ResponseConfiguration(new TextResponseFormatConfiguration(false), false);
  }

  private static ResponseFormatConfiguration format(ResponseFormatTemplateConfiguration format) {
    return switch (format) {
      case TextResponseFormatTemplateConfiguration text ->
          new TextResponseFormatConfiguration(Boolean.TRUE.equals(text.parseJson()));
      case JsonResponseFormatTemplateConfiguration json ->
          new JsonResponseFormatConfiguration(
              json.schema(),
              StringUtils.isBlank(json.schemaName())
                  ? JsonResponseFormatConfiguration.DEFAULT_SCHEMA_NAME
                  : json.schemaName());
    };
  }
}
