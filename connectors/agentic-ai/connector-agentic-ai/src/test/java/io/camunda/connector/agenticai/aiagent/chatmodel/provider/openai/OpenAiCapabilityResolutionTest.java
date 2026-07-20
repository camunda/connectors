/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.capabilities.AgenticAiCapabilitiesConfiguration;
import io.camunda.connector.agenticai.aiagent.capabilities.ModelCapabilitiesResolver;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiEffort;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bundled-matrix coverage for the typed OpenAI {@code provider.reasoning} descriptor: reasoning
 * effort-levels are declared identically for the reasoning-capable model families (gpt-5, o-series)
 * on both {@code openai-responses} and {@code openai-completions} — reasoning is a model
 * capability, not an API-surface one.
 */
class OpenAiCapabilityResolutionTest {

  private static final String OPENAI_RESPONSES = "openai-responses";
  private static final String OPENAI_COMPLETIONS = "openai-completions";

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TestObjectMapperConfig.class)
          .withUserConfiguration(AgenticAiCapabilitiesConfiguration.class);

  @Test
  void gpt5OnResponsesDeclaresReasoning() {
    contextRunner.run(
        context -> {
          final var caps = resolve(context, OPENAI_RESPONSES, "gpt-5");

          assertThat(caps.supportsReasoning()).isTrue();
          assertThat(caps.reasoning().effortLevels()).isNotEmpty();
        });
  }

  @Test
  void gpt5OnCompletionsHasReasoning() {
    contextRunner.run(
        context -> {
          final var caps = resolve(context, OPENAI_COMPLETIONS, "gpt-5");

          assertThat(caps.supportsReasoning()).isTrue();
          assertThat(caps.reasoning().effortLevels())
              .containsExactlyInAnyOrder(
                  OpenAiEffort.MINIMAL, OpenAiEffort.LOW, OpenAiEffort.MEDIUM, OpenAiEffort.HIGH);
        });
  }

  private static OpenAiModelCapabilities resolve(
      ApplicationContext context, String apiFamily, String modelId) {
    return context
        .getBean(ModelCapabilitiesResolver.class)
        .resolve(apiFamily, modelId, "direct", Optional.empty(), OpenAiModelCapabilitiesData.class);
  }

  /**
   * Provides the {@link ObjectMapper} bean that {@link AgenticAiCapabilitiesConfiguration} expects
   * (qualified with {@link ConnectorsObjectMapper}).
   */
  @Configuration
  static class TestObjectMapperConfig {
    @Bean
    @ConnectorsObjectMapper
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }
  }
}
