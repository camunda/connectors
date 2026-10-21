/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.mistral;

import io.camunda.connector.agenticai.aiagent.chatmodel.connection.EndpointAuthentication;
import io.camunda.connector.agenticai.aiagent.chatmodel.connection.RequestCustomizations;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * Configuration of the native Mistral chat model.
 *
 * @param timeout timeout of a model call
 * @param maxTokens maximum number of tokens to generate, or {@code null} for the model default
 * @param effort reasoning effort, or {@code null} for the model default
 * @param temperature sampling temperature, or {@code null} for the model default
 * @param topP nucleus sampling probability, or {@code null} for the model default
 */
public record MistralConfiguration(
    Backend backend,
    String model,
    Duration timeout,
    @Nullable Integer maxTokens,
    @Nullable Effort effort,
    @Nullable Double temperature,
    @Nullable Double topP)
    implements ChatModelConfiguration {

  public static final String PROVIDER = "mistral";

  @Override
  public String provider() {
    return PROVIDER;
  }

  @Override
  public String descriptiveProvider() {
    return "%s/%s".formatted(PROVIDER, backend.type());
  }

  /** Reasoning effort values which Mistral reasoning models accept. */
  public enum Effort {
    NONE,
    HIGH
  }

  public sealed interface Backend {

    /** The identifier of the backend, which is part of the descriptive provider. */
    String type();

    RequestCustomizations customizations();

    /** The Mistral API, or a deployment of it at another endpoint. */
    record Api(String apiKey, String endpoint, RequestCustomizations customizations)
        implements Backend {

      public static final String TYPE = "mistral-api";
      public static final String DEFAULT_ENDPOINT = "https://api.mistral.ai/v1";

      @Override
      public String type() {
        return TYPE;
      }

      @Override
      public String toString() {
        return "Api{apiKey=[REDACTED], endpoint=%s}".formatted(endpoint);
      }
    }

    /** A Mistral-compatible endpoint. */
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
