/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.gemini;

import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * Configuration of the native Gemini chat model.
 *
 * @param timeout timeout of a model call
 * @param maxTokens maximum number of tokens to generate, or {@code null} for the model default
 * @param temperature sampling temperature, or {@code null} for the model default
 * @param topP nucleus sampling probability, or {@code null} for the model default
 * @param topK top-k sampling, or {@code null} for the model default
 * @param thinking how the model thinks, or {@code null} to leave it to the model
 */
public record GeminiConfiguration(
    Backend backend,
    String model,
    Duration timeout,
    @Nullable Integer maxTokens,
    @Nullable Double temperature,
    @Nullable Double topP,
    @Nullable Integer topK,
    @Nullable Thinking thinking)
    implements ChatModelConfiguration {

  public static final String PROVIDER = "google-gemini";

  @Override
  public String provider() {
    return PROVIDER;
  }

  @Override
  public String descriptiveProvider() {
    return "%s/%s".formatted(PROVIDER, backend.type());
  }

  public sealed interface Backend {

    /** The identifier of the backend, which is part of the descriptive provider. */
    String type();

    /**
     * The Gemini Developer API.
     *
     * @param endpoint a custom base URL, or {@code null} for the one of the API
     */
    record Api(String apiKey, @Nullable String endpoint) implements Backend {

      public static final String TYPE = "google-gemini-api";

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
     * Gemini on Google Vertex AI.
     *
     * @param endpoint a custom base URL, or {@code null} for the one of the region
     */
    record VertexAi(
        String projectId, String region, @Nullable String endpoint, Authentication authentication)
        implements Backend {

      public static final String TYPE = "google-vertex-ai";

      @Override
      public String type() {
        return TYPE;
      }
    }
  }

  public sealed interface Authentication {

    record ServiceAccountKey(String jsonKey) implements Authentication {

      @Override
      public String toString() {
        return "ServiceAccountKey{jsonKey=[REDACTED]}";
      }
    }

    /** The application default credentials, which the Google SDK resolves itself. */
    record ApplicationDefaultCredentials() implements Authentication {}
  }

  /** Either a token budget (Gemini 2.5) or a thinking level (Gemini 3), never both. */
  public sealed interface Thinking {

    /**
     * @param tokens the token budget; {@code -1} lets the model decide dynamically
     */
    record Budget(int tokens) implements Thinking {}

    record Level(ThinkingLevel level) implements Thinking {}
  }

  public enum ThinkingLevel {
    MINIMAL,
    LOW,
    MEDIUM,
    HIGH
  }
}
