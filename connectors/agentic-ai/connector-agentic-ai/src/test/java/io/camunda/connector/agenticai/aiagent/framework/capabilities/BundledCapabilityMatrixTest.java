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
          // Pinned from models.dev anthropic/claude-sonnet-4-6:
          assertThat(caps.contextWindow()).isEqualTo(1000000);
          assertThat(caps.maxOutputTokens()).isEqualTo(128000);
        });
  }

  @Test
  void claudeFableFlagshipResolvesReasoningAndTokenBudgets() {
    contextRunner.run(
        context -> {
          final var caps = resolve(context, ANTHROPIC_MESSAGES, "claude-fable-5");

          assertThat(caps.supportsReasoning()).isTrue();
          assertThat(caps.supportsReasoningSignatureRoundtrip()).isTrue();
          // Pinned from models.dev anthropic/claude-fable-5:
          assertThat(caps.contextWindow()).isEqualTo(1000000);
          assertThat(caps.maxOutputTokens()).isEqualTo(128000);
        });
  }

  @Test
  void claudeOpusLegacyGenerationsPinTheirOwnTokenBudgets() {
    contextRunner.run(
        context -> {
          // claude-opus-4-1 and claude-opus-4-5 are both 200k-context releases but pin different
          // max-output-tokens (models.dev anthropic/claude-opus-4-1, anthropic/claude-opus-4-5) —
          // a single claude-opus-4-* glob would over-promise for one of them, so each gets its own
          // entry.
          final var opus41 = resolve(context, ANTHROPIC_MESSAGES, "claude-opus-4-1");
          assertThat(opus41.contextWindow()).isEqualTo(200000);
          assertThat(opus41.maxOutputTokens()).isEqualTo(32000);

          final var opus45 = resolve(context, ANTHROPIC_MESSAGES, "claude-opus-4-5");
          assertThat(opus45.contextWindow()).isEqualTo(200000);
          assertThat(opus45.maxOutputTokens()).isEqualTo(64000);

          // claude-opus-4-6 and later moved to the 1M-context tier (models.dev
          // anthropic/claude-opus-4-6):
          final var opus46 = resolve(context, ANTHROPIC_MESSAGES, "claude-opus-4-6");
          assertThat(opus46.contextWindow()).isEqualTo(1000000);
          assertThat(opus46.maxOutputTokens()).isEqualTo(128000);
        });
  }

  @Test
  void claudeHaikuResolvesReasoningWithExplicitlyPinnedContextWindow() {
    contextRunner.run(
        context -> {
          final var caps = resolve(context, ANTHROPIC_MESSAGES, "claude-haiku-4-5");

          assertThat(caps.supportsReasoning()).isTrue();
          assertThat(caps.supportsReasoningSignatureRoundtrip()).isTrue();
          // Pinned from models.dev anthropic/claude-haiku-4-5. Context window happens to equal the
          // family default (200000) but is still pinned explicitly on the entry:
          assertThat(caps.maxOutputTokens()).isEqualTo(64000);
          assertThat(caps.contextWindow()).isEqualTo(200000);
        });
  }

  @Test
  void unknownClaudeModelFallsThroughToFamilyDefaults() {
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
          // gpt-5* spans 128k-1.05M context / 16k-272k output on models.dev across its
          // chat-latest/base/codex/pro variants; the entry pins the conservative floor across that
          // whole lineage rather than gpt-5.5's own (higher) real budget:
          assertThat(caps.contextWindow()).isEqualTo(128000);
          assertThat(caps.maxOutputTokens()).isEqualTo(16384);
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
  void gpt4oInheritsDefaultModalitiesAndPinsMaxOutputTokens() {
    contextRunner.run(
        context -> {
          final var caps = resolve(context, OPENAI_RESPONSES, "gpt-4o-mini");

          // models.dev openai/gpt-4o input modalities are text/image/pdf, matching the family
          // default, so no override is needed (no audio support, unlike a previous placeholder).
          assertThat(caps.userMessageModalities())
              .containsExactly(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT);
          assertThat(caps.toolResultModalities())
              .containsExactly(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT);
          assertThat(caps.supportsReasoning()).isFalse();
          // Pinned from models.dev openai/gpt-4o:
          assertThat(caps.maxOutputTokens()).isEqualTo(16384);
        });
  }

  @Test
  void gpt4oInitialReleasePinsLowerMaxOutputTokensByExactId() {
    contextRunner.run(
        context -> {
          // gpt-4o-2024-05-13's initial release capped max-output-tokens at 4096, below every
          // later gpt-4o* release (models.dev openai/gpt-4o-2024-05-13). A gpt-4o* glob pinned at
          // 16384 would over-promise for this one dated snapshot, so it gets its own exact-id
          // entry that wins over the pattern match.
          final var caps = resolve(context, OPENAI_COMPLETIONS, "gpt-4o-2024-05-13");

          assertThat(caps.contextWindow()).isEqualTo(128000);
          assertThat(caps.maxOutputTokens()).isEqualTo(4096);
        });
  }

  @Test
  void o1OnCompletionsHasReasoningButNoParallelToolCalls() {
    contextRunner.run(
        context -> {
          final var caps = resolve(context, OPENAI_COMPLETIONS, "o1-mini");

          assertThat(caps.supportsReasoning()).isTrue();
          assertThat(caps.supportsParallelToolCalls()).isFalse();
          // Pinned from models.dev openai/o1:
          assertThat(caps.contextWindow()).isEqualTo(200000);
          assertThat(caps.maxOutputTokens()).isEqualTo(100000);
        });
  }

  @Test
  void o4DropsDocumentSupportFromUserMessageModalities() {
    contextRunner.run(
        context -> {
          final var caps = resolve(context, OPENAI_COMPLETIONS, "o4-mini");

          assertThat(caps.supportsReasoning()).isTrue();
          // models.dev openai/o4-mini input modalities are text/image only (no pdf), unlike the
          // family default which includes document:
          assertThat(caps.userMessageModalities()).containsExactly(Modality.TEXT, Modality.IMAGE);
          assertThat(caps.contextWindow()).isEqualTo(200000);
          assertThat(caps.maxOutputTokens()).isEqualTo(100000);
        });
  }

  @Test
  void gpt41ResolvesLargeContextWithoutReasoning() {
    contextRunner.run(
        context -> {
          final var caps = resolve(context, OPENAI_COMPLETIONS, "gpt-4.1");

          assertThat(caps.supportsReasoning()).isFalse();
          // Pinned from models.dev openai/gpt-4.1:
          assertThat(caps.contextWindow()).isEqualTo(1047576);
          assertThat(caps.maxOutputTokens()).isEqualTo(32768);
        });
  }

  @Test
  void unknownOpenAiModelFallsThroughToFamilyDefaults() {
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
        .resolve(apiFamily, modelId, null, Optional.empty());
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
