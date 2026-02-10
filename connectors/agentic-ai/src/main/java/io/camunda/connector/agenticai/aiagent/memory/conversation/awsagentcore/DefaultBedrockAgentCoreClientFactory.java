/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore;

import io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.AwsAgentCoreConversationStore.BedrockAgentCoreClientFactory;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.AwsAgentCoreAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.AwsAgentCoreMemoryStorageConfiguration;
import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClientBuilder;

/**
 * Default implementation of {@link BedrockAgentCoreClientFactory} that creates AWS Bedrock
 * AgentCore clients based on the provided configuration.
 *
 * <p>Supports authentication via static credentials or default credentials chain, optional region
 * and endpoint override configuration.
 */
public class DefaultBedrockAgentCoreClientFactory implements BedrockAgentCoreClientFactory {

  @Override
  public BedrockAgentCoreClient createClient(AwsAgentCoreMemoryStorageConfiguration config) {
    BedrockAgentCoreClientBuilder builder = BedrockAgentCoreClient.builder();

    // Apply authentication
    builder.credentialsProvider(createCredentialsProvider(config.authentication()));

    // Apply region if specified
    if (config.region() != null && !config.region().isBlank()) {
      builder.region(Region.of(config.region()));
    }

    // Apply endpoint override if specified (useful for testing/mocking)
    if (config.endpointOverride() != null) {
      builder.endpointOverride(URI.create(config.endpointOverride()));
    }

    return builder.build();
  }

  private AwsCredentialsProvider createCredentialsProvider(
      AwsAgentCoreAuthentication authentication) {
    if (authentication
        instanceof AwsAgentCoreAuthentication.AwsStaticCredentialsAuthentication credentials) {
      return StaticCredentialsProvider.create(
          AwsBasicCredentials.create(credentials.accessKey(), credentials.secretKey()));
    } else if (authentication
        instanceof AwsAgentCoreAuthentication.AwsDefaultCredentialsChainAuthentication) {
      return DefaultCredentialsProvider.builder().build();
    } else {
      throw new IllegalArgumentException(
          "Unsupported authentication type: " + authentication.getClass().getName());
    }
  }
}
