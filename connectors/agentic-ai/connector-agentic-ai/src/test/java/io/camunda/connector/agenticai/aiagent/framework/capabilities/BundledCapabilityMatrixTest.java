/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.capabilities;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities.Modality;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring-Boot-style integration test for the bundled capability matrix. {@link
 * AgenticAiCapabilitiesConfiguration} loads the bundled YAML as a {@link
 * org.springframework.core.env.PropertySource} during its own {@code setEnvironment(...)} callback,
 * so importing the configuration class is enough — no manual environment post-processing needed.
 * The test then exercises the full {@link AgenticAiFrameworkProperties} -> {@link
 * CapabilityMatrixFactory} -> {@link ModelCapabilitiesResolver} pipeline.
 */
class BundledCapabilityMatrixTest {

  private static final String ANTHROPIC_MESSAGES = "anthropic-messages";
  private static final String OPENAI_COMPLETIONS = "openai-completions";
  private static final String OPENAI_RESPONSES = "openai-responses";

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
              .containsKeys(ANTHROPIC_MESSAGES, OPENAI_COMPLETIONS, OPENAI_RESPONSES);
        });
  }

  @Test
  void claudeSonnet4FlagshipResolvesReasoningAndMultimodal() {
    contextRunner.run(
        context -> {
          final var caps = resolve(context, ANTHROPIC_MESSAGES, "claude-sonnet-4-6");

          assertThat(caps.userMessageModalities())
              .containsExactly(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT);
          assertThat(caps.toolResultModalities())
              .containsExactly(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT);
          assertThat(caps.supportsReasoning()).isTrue();
          assertThat(caps.supportsReasoningSignatureRoundtrip()).isTrue();
          assertThat(caps.supportsPromptCaching()).isTrue();
          assertThat(caps.supportsParallelToolCalls()).isTrue();
          // Token budgets are not pinned per-model (provisional matrix): they inherit the
          // conservative family default until authoritatively curated.
          assertThat(caps.maxOutputTokens()).isEqualTo(8192);
        });
  }

  @Test
  void claudeFableFlagshipResolvesReasoningAndInheritsConservativeTokenBudgets() {
    contextRunner.run(
        context -> {
          final var caps = resolve(context, ANTHROPIC_MESSAGES, "claude-fable-5");

          assertThat(caps.supportsReasoning()).isTrue();
          assertThat(caps.supportsReasoningSignatureRoundtrip()).isTrue();
          // Inherited family defaults (not pinned until authoritatively curated):
          assertThat(caps.contextWindow()).isEqualTo(200000);
          assertThat(caps.maxOutputTokens()).isEqualTo(8192);
        });
  }

  @Test
  void unknownClaudeModelFallsThroughToFamilyCatchAll() {
    contextRunner.run(
        context -> {
          final var caps = resolve(context, ANTHROPIC_MESSAGES, "claude-some-future-model");

          assertThat(caps.userMessageModalities())
              .containsExactly(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT);
          assertThat(caps.supportsPromptCaching()).isTrue();
          assertThat(caps.supportsReasoning()).isFalse();
        });
  }

  @Test
  void gpt5OnCompletionsHasReasoningButTextOnlyToolResults() {
    contextRunner.run(
        context -> {
          final var caps = resolve(context, OPENAI_COMPLETIONS, "gpt-5.5");

          assertThat(caps.supportsReasoning()).isTrue();
          assertThat(caps.toolResultModalities()).containsExactly(Modality.TEXT);
          // Token budget inherited from the family default (not pinned per-model yet):
          assertThat(caps.contextWindow()).isEqualTo(128000);
        });
  }

  @Test
  void gpt5OnResponsesHasReasoningRoundtripAndMultimodalToolResults() {
    contextRunner.run(
        context -> {
          final var caps = resolve(context, OPENAI_RESPONSES, "gpt-5.5");

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
          final var caps = resolve(context, OPENAI_RESPONSES, "gpt-4o-mini");

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
          final var caps = resolve(context, OPENAI_COMPLETIONS, "o1-mini");

          assertThat(caps.supportsReasoning()).isTrue();
          assertThat(caps.supportsParallelToolCalls()).isFalse();
        });
  }

  @Test
  void unknownOpenAiModelFallsThroughToFamilyCatchAll() {
    contextRunner.run(
        context -> {
          final var caps = resolve(context, OPENAI_COMPLETIONS, "gpt-3.5-turbo");

          assertThat(caps.contextWindow()).isEqualTo(128000);
          assertThat(caps.supportsReasoning()).isFalse();
        });
  }

  private static ModelCapabilities resolve(
      ApplicationContext context, String apiFamily, String modelId) {
    return context
        .getBean(ModelCapabilitiesResolver.class)
        .resolve(apiFamily, modelId, Optional.empty());
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
