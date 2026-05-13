/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.capabilities;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.framework.api.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.api.ModelCapabilities.Modality;
import io.camunda.connector.agenticai.aiagent.framework.openai.OpenAiChatModelApiFactory;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring-Boot-style integration test for the bundled capability matrix. {@link
 * AgenticAiCapabilitiesConfiguration} loads the bundled YAML as a {@link
 * org.springframework.core.env.PropertySource} during its own {@code setEnvironment(...)} callback,
 * so importing the configuration class is enough — no manual environment post-processing needed.
 * The test then exercises the full {@link AgenticAiFrameworkProperties} → {@link
 * CapabilityMatrixFactory} → {@link ModelCapabilitiesResolver} pipeline.
 */
class BundledCapabilityMatrixTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TestObjectMapperConfig.class)
          .withUserConfiguration(AgenticAiCapabilitiesConfiguration.class);

  @Test
  void coversAllShippedApiFamilies() {
    contextRunner.run(
        context -> {
          final var matrix = context.getBean(CapabilityMatrix.class);
          assertThat(matrix.families())
              .containsKeys(
                  "anthropic-messages",
                  OpenAiChatModelApiFactory.API_FAMILY_COMPLETIONS,
                  OpenAiChatModelApiFactory.API_FAMILY_RESPONSES);
        });
  }

  @Test
  void claudeSonnet4ResolvesToFullCapabilities() {
    contextRunner.run(
        context -> {
          final var caps = resolve(context, "anthropic-messages", "claude-sonnet-4-6");

          assertThat(caps.userMessageModalities())
              .containsExactly(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT);
          assertThat(caps.toolResultModalities())
              .containsExactly(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT);
          assertThat(caps.supportsReasoning()).isTrue();
          assertThat(caps.supportsReasoningSignatureRoundtrip()).isTrue();
          assertThat(caps.supportsPromptCaching()).isTrue();
          assertThat(caps.supportsParallelToolCalls()).isTrue();
          assertThat(caps.maxOutputTokens()).isEqualTo(64000);
        });
  }

  @Test
  void claudeHaiku4InheritsToolResultButOverridesUserMessage() {
    contextRunner.run(
        context -> {
          final var caps = resolve(context, "anthropic-messages", "claude-haiku-4-5");

          assertThat(caps.userMessageModalities()).containsExactly(Modality.TEXT, Modality.IMAGE);
          assertThat(caps.toolResultModalities())
              .containsExactly(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT);
          assertThat(caps.supportsReasoning()).isFalse();
        });
  }

  @Test
  void unknownClaudeModelFallsThroughToFamilyCatchAll() {
    contextRunner.run(
        context -> {
          final var caps = resolve(context, "anthropic-messages", "claude-some-future-model");

          assertThat(caps.userMessageModalities())
              .containsExactly(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT);
          assertThat(caps.supportsPromptCaching()).isTrue();
        });
  }

  @Test
  void gpt5OnCompletionsHasReasoningButNotRoundtrip() {
    contextRunner.run(
        context -> {
          final var caps =
              resolve(context, OpenAiChatModelApiFactory.API_FAMILY_COMPLETIONS, "gpt-5");

          assertThat(caps.supportsReasoning()).isTrue();
          assertThat(caps.supportsReasoningSignatureRoundtrip()).isFalse();
          assertThat(caps.toolResultModalities()).containsExactly(Modality.TEXT);
          assertThat(caps.contextWindow()).isEqualTo(400000);
        });
  }

  @Test
  void gpt5OnResponsesHasReasoningRoundtripAndMultimodalToolResults() {
    contextRunner.run(
        context -> {
          final var caps =
              resolve(context, OpenAiChatModelApiFactory.API_FAMILY_RESPONSES, "gpt-5");

          assertThat(caps.supportsReasoning()).isTrue();
          assertThat(caps.supportsReasoningSignatureRoundtrip()).isTrue();
          assertThat(caps.toolResultModalities())
              .containsExactly(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT);
        });
  }

  @Test
  void gpt4oAddsAudioToUserMessageButKeepsToolResultFromDefaults() {
    contextRunner.run(
        context -> {
          final var caps =
              resolve(context, OpenAiChatModelApiFactory.API_FAMILY_RESPONSES, "gpt-4o-mini");

          assertThat(caps.userMessageModalities())
              .containsExactly(Modality.TEXT, Modality.IMAGE, Modality.AUDIO);
          assertThat(caps.toolResultModalities())
              .containsExactly(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT);
          assertThat(caps.supportsReasoning()).isFalse();
        });
  }

  @Test
  void o1OnCompletionsHasReasoningButNoParallelToolCalls() {
    contextRunner.run(
        context -> {
          final var caps =
              resolve(context, OpenAiChatModelApiFactory.API_FAMILY_COMPLETIONS, "o1-mini");

          assertThat(caps.supportsReasoning()).isTrue();
          assertThat(caps.supportsParallelToolCalls()).isFalse();
        });
  }

  @Test
  void gpt41OnResponsesHasLargeContextWindowDespiteDotInPattern() {
    contextRunner.run(
        context -> {
          final var caps =
              resolve(context, OpenAiChatModelApiFactory.API_FAMILY_RESPONSES, "gpt-4.1-mini");

          assertThat(caps.contextWindow()).isEqualTo(1000000);
          assertThat(caps.maxOutputTokens()).isEqualTo(32768);
        });
  }

  private static ModelCapabilities resolve(
      org.springframework.context.ApplicationContext context, String apiFamily, String modelId) {
    return context
        .getBean(ModelCapabilitiesResolver.class)
        .resolve(apiFamily, modelId, Optional.empty());
  }

  /**
   * Provides the {@link com.fasterxml.jackson.databind.ObjectMapper} bean that {@link
   * AgenticAiCapabilitiesConfiguration} expects (qualified with {@link ConnectorsObjectMapper}).
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
