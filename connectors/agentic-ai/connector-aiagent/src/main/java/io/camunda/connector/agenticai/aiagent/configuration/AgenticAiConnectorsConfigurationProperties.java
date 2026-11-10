/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.configuration;

import io.camunda.connector.agenticai.aiagent.document.DocumentContentLimits;
import io.camunda.connector.agenticai.common.retry.RetriesProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "camunda.connector.agenticai")
public record AgenticAiConnectorsConfigurationProperties(
    @Valid @DefaultValue AiAgentProperties aiagent) {

  /**
   * AI Agent connector settings.
   *
   * @param rewriteV1ProviderConfigToV2 when {@code true} (the default), v1 provider configurations
   *     are rewritten to their v2 equivalent and executed through the v2 provider path. When {@code
   *     false}, the original v1 path is used. This is a temporary switch, removed when the v1 path
   *     is dropped.
   */
  public record AiAgentProperties(
      @Valid @DefaultValue ChatModelProperties chatModel,
      @Valid @DefaultValue AgentInstanceProperties agentInstance,
      @Valid @DefaultValue DocumentProperties document,
      @DefaultValue("true") boolean rewriteV1ProviderConfigToV2) {

    public record AgentInstanceProperties(
        @Valid @DefaultValue @NestedConfigurationProperty RetriesProperties retries) {}

    public record DocumentProperties(@Valid @DefaultValue ContentLimitsProperties contentLimits) {

      /**
       * The size limits of the content the agent adds to the conversation. A document above the
       * limit of its kind is not loaded: the model gets its handle only. The defaults are the ones
       * of {@link DocumentContentLimits#defaults()}: the limits of the most restrictive supported
       * model provider, which apply to every provider. Every limit must be positive.
       *
       * @param text the limit of a text-based document
       * @param pdf the limit of a PDF document
       * @param image the limit of an image
       */
      public record ContentLimitsProperties(
          @DefaultValue("256KB") DataSize text,
          @DefaultValue("3584KB") DataSize pdf,
          @DefaultValue("3584KB") DataSize image) {

        @AssertTrue(message = "text must be positive")
        public boolean isTextPositive() {
          return isPositive(text);
        }

        @AssertTrue(message = "pdf must be positive")
        public boolean isPdfPositive() {
          return isPositive(pdf);
        }

        @AssertTrue(message = "image must be positive")
        public boolean isImagePositive() {
          return isPositive(image);
        }

        /** The limits to apply. */
        public DocumentContentLimits toLimits() {
          return new DocumentContentLimits(text.toBytes(), pdf.toBytes(), image.toBytes());
        }

        private static boolean isPositive(DataSize size) {
          return size.toBytes() > 0;
        }
      }
    }
  }

  public record ChatModelProperties(
      @Valid @DefaultValue ApiProperties api, @Valid @DefaultValue AzureProperties azure) {
    public record ApiProperties(@DefaultValue("PT3M") Duration defaultTimeout) {}

    public record AzureProperties(@Valid @DefaultValue CredentialCacheProperties credentialCache) {
      public record CredentialCacheProperties(
          @DefaultValue("true") boolean enabled,
          @DefaultValue("100") @PositiveOrZero Long maximumSize,
          @DefaultValue("PT10M") Duration expireAfterAccess) {}
    }
  }
}
