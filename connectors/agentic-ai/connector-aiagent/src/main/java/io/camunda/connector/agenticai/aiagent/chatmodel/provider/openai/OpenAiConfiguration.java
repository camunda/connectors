/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import io.camunda.connector.agenticai.aiagent.chatmodel.connection.EndpointAuthentication;
import io.camunda.connector.agenticai.aiagent.chatmodel.connection.RequestCustomizations;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.azure.FoundryAuthentication;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * Configuration of the native OpenAI chat model: an API family, spoken to a backend.
 *
 * @param timeout timeout of a model call
 */
public record OpenAiConfiguration(Api api, Backend backend, String model, Duration timeout)
    implements ChatModelConfiguration {

  public static final String PROVIDER = "openai";

  @Override
  public String provider() {
    return PROVIDER;
  }

  @Override
  public String descriptiveProvider() {
    return "%s/%s/%s".formatted(PROVIDER, api.type(), backend.type());
  }

  /** Reasoning effort levels, trading thoroughness against speed and cost. */
  public enum Effort {
    MINIMAL,
    LOW,
    MEDIUM,
    HIGH,
    XHIGH,
    MAX
  }

  /** The API family of the model call. Each option is {@code null} for the model default. */
  public sealed interface Api {

    /** The identifier of the API family, which is part of the descriptive provider. */
    String type();

    @Nullable Effort effort();

    @Nullable Double temperature();

    @Nullable Double topP();

    /**
     * The Responses API.
     *
     * @param maxOutputTokens maximum number of tokens to generate
     */
    record Responses(
        @Nullable Integer maxOutputTokens,
        @Nullable Effort effort,
        @Nullable Double temperature,
        @Nullable Double topP)
        implements Api {

      public static final String TYPE = "responses";

      @Override
      public String type() {
        return TYPE;
      }
    }

    /**
     * The Chat Completions API.
     *
     * @param maxCompletionTokens maximum number of tokens to generate
     */
    record Completions(
        @Nullable Integer maxCompletionTokens,
        @Nullable Effort effort,
        @Nullable Double temperature,
        @Nullable Double topP)
        implements Api {

      public static final String TYPE = "completions";

      @Override
      public String type() {
        return TYPE;
      }
    }
  }

  public sealed interface Backend {

    /** The identifier of the backend, which is part of the descriptive provider. */
    String type();

    /** The configured base URL, or {@code null} for the default one of the backend. */
    @Nullable String endpoint();

    RequestCustomizations customizations();

    /** The OpenAI API. */
    record Api(
        String apiKey,
        @Nullable String organizationId,
        @Nullable String projectId,
        @Nullable String endpoint,
        RequestCustomizations customizations)
        implements Backend {

      public static final String TYPE = "openai-api";

      @Override
      public String type() {
        return TYPE;
      }

      @Override
      public String toString() {
        return "Api{apiKey=[REDACTED], organizationId=%s, projectId=%s, endpoint=%s}"
            .formatted(organizationId, projectId, endpoint);
      }
    }

    /**
     * OpenAI on Microsoft Foundry.
     *
     * @param apiVersion a version of the API to pin, or {@code null} for implicit versioning
     */
    record Foundry(
        String endpoint,
        @Nullable String apiVersion,
        FoundryAuthentication authentication,
        RequestCustomizations customizations)
        implements Backend {

      public static final String TYPE = "foundry";

      @Override
      public String type() {
        return TYPE;
      }
    }

    /** An OpenAI-compatible endpoint. */
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
