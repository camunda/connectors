/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import io.camunda.connector.agenticai.aiagent.chatmodel.connection.EndpointAuthentication;
import io.camunda.connector.agenticai.aiagent.chatmodel.connection.RequestCustomizations;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.aws.AwsAuthentication;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.FoundryAuthentication;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * Configuration of the native Anthropic chat model.
 *
 * @param timeout timeout of a model call
 * @param effort how many tokens the model spends when responding, or {@code null} for the model
 *     default
 * @param thinking the extended thinking mechanism, or {@code null} for the model default
 * @param promptCaching whether to enable automatic prompt caching
 * @param maxTokens maximum number of tokens to generate
 * @param temperature sampling temperature, or {@code null} for the model default
 * @param topP nucleus sampling probability, or {@code null} for the model default
 * @param topK number of candidate tokens to sample from, or {@code null} for the model default
 */
public record AnthropicConfiguration(
    Backend backend,
    String model,
    Duration timeout,
    @Nullable Effort effort,
    @Nullable Thinking thinking,
    boolean promptCaching,
    int maxTokens,
    @Nullable Double temperature,
    @Nullable Double topP,
    @Nullable Integer topK)
    implements ChatModelConfiguration {

  public static final String PROVIDER = "anthropic";

  /** The maximum number of tokens to generate when the request does not configure it. */
  public static final int DEFAULT_MAX_TOKENS = 4096;

  @Override
  public String provider() {
    return PROVIDER;
  }

  @Override
  public String descriptiveProvider() {
    return "%s/%s".formatted(PROVIDER, backend.type());
  }

  /** How many tokens the model spends when responding, trading thoroughness against speed. */
  public enum Effort {
    LOW,
    MEDIUM,
    HIGH,
    XHIGH,
    MAX
  }

  /** The extended thinking mechanism of the model. */
  public sealed interface Thinking {

    /** A manual token budget, for older models. */
    record Enabled(int budgetTokens) implements Thinking {}

    /** Thinking managed by the model, for newer models. */
    record Adaptive(@Nullable Display display) implements Thinking {}

    record Disabled() implements Thinking {}
  }

  /** How the thinking of an adaptive model is returned. */
  public enum Display {
    SUMMARIZED,
    OMITTED
  }

  public sealed interface Backend {

    /** The identifier of the backend, which is part of the descriptive provider. */
    String type();

    /** The configured base URL, or {@code null} for the default one of the backend. */
    @Nullable String endpoint();

    RequestCustomizations customizations();

    /** The Anthropic API. */
    record Api(String apiKey, @Nullable String endpoint, RequestCustomizations customizations)
        implements Backend {

      public static final String TYPE = "anthropic-api";

      @Override
      public String type() {
        return TYPE;
      }

      @Override
      public String toString() {
        return "Api{apiKey=[REDACTED], endpoint=%s}".formatted(endpoint);
      }
    }

    /**
     * Anthropic on AWS Bedrock Mantle.
     *
     * @param endpoint the full base URL including the {@code /anthropic} path segment, which
     *     replaces the default of the region
     */
    record AwsBedrockMantle(
        String region,
        @Nullable String endpoint,
        AwsAuthentication authentication,
        RequestCustomizations customizations)
        implements Backend {

      public static final String TYPE = "aws-bedrock-mantle";

      @Override
      public String type() {
        return TYPE;
      }
    }

    /** Anthropic on Microsoft Foundry. */
    record Foundry(
        String endpoint, FoundryAuthentication authentication, RequestCustomizations customizations)
        implements Backend {

      public static final String TYPE = "foundry";

      @Override
      public String type() {
        return TYPE;
      }
    }

    /** An Anthropic-compatible endpoint. */
    record Custom(
        String endpoint,
        EndpointAuthentication authentication,
        RequestCustomizations customizations)
        implements Backend {

      public static final String TYPE = "custom";

      @Override
      public String type() {
        return TYPE;
      }
    }
  }
}
