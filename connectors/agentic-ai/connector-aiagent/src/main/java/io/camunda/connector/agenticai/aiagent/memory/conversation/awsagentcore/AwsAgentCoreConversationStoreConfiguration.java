/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore;

import io.camunda.connector.agenticai.aiagent.model.request.ConversationStoreConfiguration;
import org.jspecify.annotations.Nullable;

/**
 * Configuration of the AWS AgentCore Memory conversation store.
 *
 * @param region the AWS region
 * @param endpoint a custom API endpoint, or {@code null} to use the endpoint of the region
 * @param authentication how to authenticate against AWS
 * @param memoryId the ID of the pre-provisioned AgentCore Memory resource
 * @param actorId the identifier of the actor associated with the events
 */
public record AwsAgentCoreConversationStoreConfiguration(
    String region,
    @Nullable String endpoint,
    Authentication authentication,
    String memoryId,
    String actorId)
    implements ConversationStoreConfiguration {

  @Override
  public String storeType() {
    return AwsAgentCoreConversationStore.TYPE;
  }

  public sealed interface Authentication {

    record StaticCredentials(String accessKey, String secretKey) implements Authentication {

      @Override
      public String toString() {
        return "StaticCredentials{accessKey=[REDACTED], secretKey=[REDACTED]}";
      }
    }

    record DefaultCredentialsChain() implements Authentication {}
  }
}
