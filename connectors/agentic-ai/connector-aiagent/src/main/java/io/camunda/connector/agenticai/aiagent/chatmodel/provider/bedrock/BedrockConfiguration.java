/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock;

import io.camunda.connector.agenticai.aiagent.chatmodel.connection.RequestCustomizations;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.aws.AwsAuthentication;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * Configuration of the native Bedrock Converse chat model.
 *
 * @param endpoint a custom endpoint, or {@code null} for the one of the region
 * @param timeout timeout of a model call
 * @param promptCaching whether to place prompt caching checkpoints
 * @param maxTokens maximum number of tokens to generate, or {@code null} for the model default
 * @param temperature sampling temperature, or {@code null} for the model default
 * @param topP nucleus sampling probability, or {@code null} for the model default
 */
public record BedrockConfiguration(
    String region,
    @Nullable String endpoint,
    AwsAuthentication authentication,
    RequestCustomizations customizations,
    Duration timeout,
    String model,
    boolean promptCaching,
    @Nullable Integer maxTokens,
    @Nullable Double temperature,
    @Nullable Double topP)
    implements ChatModelConfiguration {

  public static final String PROVIDER = "bedrock";

  @Override
  public String provider() {
    return PROVIDER;
  }
}
