/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.AwsAgentCoreConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.document.CamundaDocumentConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationStore;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Map;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = MemoryStorageConfiguration.InProcessMemoryStorageConfiguration.class,
      name = InProcessConversationStore.TYPE),
  @JsonSubTypes.Type(
      value = MemoryStorageConfiguration.CamundaDocumentMemoryStorageConfiguration.class,
      name = CamundaDocumentConversationStore.TYPE),
  @JsonSubTypes.Type(
      value = MemoryStorageConfiguration.AwsAgentCoreMemoryStorageConfiguration.class,
      name = AwsAgentCoreConversationStore.TYPE),
  @JsonSubTypes.Type(
      value = MemoryStorageConfiguration.CustomMemoryStorageConfiguration.class,
      name = "custom")
})
@TemplateDiscriminatorProperty(
    label = "Memory storage type",
    group = "memory",
    name = "type",
    description = "Specify how to store the conversation memory.",
    defaultValue = InProcessConversationStore.TYPE)
public sealed interface MemoryStorageConfiguration
    permits MemoryStorageConfiguration.InProcessMemoryStorageConfiguration,
        MemoryStorageConfiguration.CamundaDocumentMemoryStorageConfiguration,
        MemoryStorageConfiguration.AwsAgentCoreMemoryStorageConfiguration,
        MemoryStorageConfiguration.CustomMemoryStorageConfiguration {

  String storeType();

  @TemplateSubType(
      id = InProcessConversationStore.TYPE,
      label = "In Process (part of agent context)")
  record InProcessMemoryStorageConfiguration() implements MemoryStorageConfiguration {
    @Override
    public String storeType() {
      return InProcessConversationStore.TYPE;
    }
  }

  @TemplateSubType(id = CamundaDocumentConversationStore.TYPE, label = "Camunda Document Storage")
  record CamundaDocumentMemoryStorageConfiguration(
      @TemplateProperty(
              label = "Document TTL",
              description =
                  "How long to retain the conversation document as ISO-8601 duration (example: <code>P14D</code>).",
              tooltip =
                  "Will use the cluster default TTL (time-to-live) if not specified. Make sure to set this value to a reasonable duration "
                      + "matching your process lifecycle.",
              optional = true)
          Duration timeToLive,
      @FEEL
          @TemplateProperty(
              label = "Custom document properties",
              description =
                  "An optional map of custom properties to be stored with the conversation document.",
              feel = Property.FeelMode.required,
              optional = true)
          Map<String, Object> customProperties)
      implements MemoryStorageConfiguration {
    @Override
    public String storeType() {
      return CamundaDocumentConversationStore.TYPE;
    }
  }

  @TemplateSubType(id = AwsAgentCoreConversationStore.TYPE, label = "AWS AgentCore Memory")
  record AwsAgentCoreMemoryStorageConfiguration(
      @FEEL
          @TemplateProperty(
              label = "Memory ID",
              description = "The ID of the pre-provisioned AgentCore Memory resource.",
              feel = Property.FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          @NotBlank
          String memoryId,
      @FEEL
          @TemplateProperty(
              label = "Actor ID",
              description =
                  "Identifier of the actor associated with events (e.g., end-user or agent/user combination).",
              feel = Property.FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          @NotBlank
          String actorId,
      @TemplateProperty(
              label = "AWS Region",
              description =
                  "The AWS region where the AgentCore Memory resource is located. Falls back to the default AWS region provider chain if not specified.",
              optional = true)
          String region,
      @TemplateProperty(
              label = "Endpoint Override",
              description = "Optional endpoint override URI for testing or custom deployments.",
              optional = true)
          String endpointOverride,
      @Valid @NotNull AwsAgentCoreAuthentication authentication)
      implements MemoryStorageConfiguration {
    @Override
    public String storeType() {
      return AwsAgentCoreConversationStore.TYPE;
    }
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(
        value = AwsAgentCoreAuthentication.AwsStaticCredentialsAuthentication.class,
        name = "credentials"),
    @JsonSubTypes.Type(
        value = AwsAgentCoreAuthentication.AwsDefaultCredentialsChainAuthentication.class,
        name = "defaultCredentialsChain")
  })
  @TemplateDiscriminatorProperty(
      label = "Authentication",
      group = "memory",
      name = "type",
      defaultValue = "credentials",
      description = "Specify the AWS authentication strategy for AgentCore Memory access.")
  sealed interface AwsAgentCoreAuthentication {

    @TemplateSubType(id = "credentials", label = "Credentials")
    record AwsStaticCredentialsAuthentication(
        @TemplateProperty(
                group = "memory",
                label = "Access key",
                description =
                    "Provide an IAM access key with permissions for bedrock-agentcore:CreateEvent and bedrock-agentcore:ListEvents")
            @NotBlank
            String accessKey,
        @TemplateProperty(
                group = "memory",
                label = "Secret key",
                description = "Provide the secret key for the IAM access key")
            @NotBlank
            String secretKey)
        implements AwsAgentCoreAuthentication {

      @Override
      public String toString() {
        return "AwsStaticCredentialsAuthentication{accessKey=[REDACTED], secretKey=[REDACTED]}";
      }
    }

    @TemplateSubType(
        id = "defaultCredentialsChain",
        label = "Default Credentials Chain (Hybrid/Self-Managed only)")
    record AwsDefaultCredentialsChainAuthentication() implements AwsAgentCoreAuthentication {}
  }

  @TemplateSubType(id = "custom", label = "Custom Implementation (Hybrid/Self-Managed only)")
  record CustomMemoryStorageConfiguration(
      @FEEL
          @TemplateProperty(
              label = "Implementation type",
              type = TemplateProperty.PropertyType.String,
              feel = Property.FeelMode.optional,
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          @NotBlank
          String storeType,
      @FEEL
          @TemplateProperty(
              label = "Parameters",
              description = "Parameters for the custom memory storage implementation.",
              feel = Property.FeelMode.required,
              optional = true)
          Map<String, Object> parameters)
      implements MemoryStorageConfiguration {}
}
