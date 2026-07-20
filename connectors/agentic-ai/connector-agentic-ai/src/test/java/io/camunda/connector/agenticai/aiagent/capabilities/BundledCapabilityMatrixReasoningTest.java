/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.capabilities;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.capabilities.BundledCapabilityMatrixTest.TestObjectMapperConfig;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicEffort;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicModelCapabilities;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.AnthropicModelCapabilitiesData;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic.ThinkingMode;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;

/**
 * Bundled-matrix coverage for the typed Anthropic {@code provider.reasoning} descriptor (see {@link
 * BundledCapabilityMatrixTest} for the general resolver/matrix pipeline coverage this extends).
 */
class BundledCapabilityMatrixReasoningTest {

  private static final String ANTHROPIC_MESSAGES = "anthropic-messages";

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TestObjectMapperConfig.class)
          .withUserConfiguration(AgenticAiCapabilitiesConfiguration.class);

  @Test
  void opus48ResolvesAdaptiveDisabledWithFullEffortLevels() {
    contextRunner.run(
        context -> {
          final var caps = resolve(context, "claude-opus-4-8");

          assertThat(caps.supportsReasoning()).isTrue();
          assertThat(caps.reasoning().thinkingModes())
              .containsExactly(ThinkingMode.ADAPTIVE, ThinkingMode.DISABLED);
          assertThat(caps.reasoning().effortLevels())
              .containsExactly(
                  AnthropicEffort.LOW,
                  AnthropicEffort.MEDIUM,
                  AnthropicEffort.HIGH,
                  AnthropicEffort.XHIGH,
                  AnthropicEffort.MAX);
        });
  }

  @Test
  void sonnet45ResolvesEnabledDisabledWithNoEffort() {
    contextRunner.run(
        context -> {
          final var caps = resolve(context, "claude-sonnet-4-5");

          assertThat(caps.reasoning().thinkingModes())
              .containsExactly(ThinkingMode.ENABLED, ThinkingMode.DISABLED);
          assertThat(caps.reasoning().effortLevels()).isEmpty();
        });
  }

  @Test
  void unknownModelHasNoReasoningDescriptor() {
    contextRunner.run(
        context -> {
          final var caps = resolve(context, "claude-mystery-9");

          assertThat(caps.reasoning()).isNull();
          assertThat(caps.supportsReasoning()).isFalse();
        });
  }

  @Test
  void familyDefaultsDeclareNoReasoning() {
    // guards the "reasoning never in defaults" invariant (spec §3c): a family-default reasoning
    // block would leak into every non-reasoning model. Assert the bundled defaults omit it.
    contextRunner.run(
        context -> {
          final var caps = resolve(context, "totally-unmatched");

          assertThat(caps.reasoning()).isNull();
        });
  }

  private static AnthropicModelCapabilities resolve(ApplicationContext context, String modelId) {
    return context
        .getBean(ModelCapabilitiesResolver.class)
        .resolve(
            ANTHROPIC_MESSAGES,
            modelId,
            "direct",
            Optional.empty(),
            AnthropicModelCapabilitiesData.class);
  }
}
