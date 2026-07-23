/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.capabilities.CoreModelCapabilities;
import io.camunda.connector.agenticai.aiagent.capabilities.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.capabilities.ModelCapabilities.Modality;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnthropicModelCapabilitiesTest {

  private static final CoreModelCapabilities CORE =
      new CoreModelCapabilities(
          List.of(Modality.TEXT, Modality.IMAGE),
          List.of(Modality.TEXT),
          List.of(Modality.TEXT),
          200000,
          8192);

  @Test
  void delegatesModalityMethodsToCore() {
    final ModelCapabilities caps = new AnthropicModelCapabilities(CORE, null);

    assertThat(caps.userMessageModalities()).containsExactly(Modality.TEXT, Modality.IMAGE);
    assertThat(caps.toolResultModalities()).containsExactly(Modality.TEXT);
    assertThat(caps.assistantMessageModalities()).containsExactly(Modality.TEXT);
  }

  @Test
  void reportsSupportsReasoningTrueAndExposesDescriptorWhenReasoningPresent() {
    final var reasoning =
        new AnthropicReasoningCapabilities(
            List.of(ThinkingMode.ADAPTIVE, ThinkingMode.DISABLED),
            List.of(AnthropicEffort.LOW, AnthropicEffort.HIGH));
    final var caps = new AnthropicModelCapabilities(CORE, reasoning);

    assertThat(caps.supportsReasoning()).isTrue();
    assertThat(caps.reasoning()).isSameAs(reasoning);
    assertThat(caps.reasoning().thinkingModes())
        .containsExactly(ThinkingMode.ADAPTIVE, ThinkingMode.DISABLED);
    assertThat(caps.reasoning().effortLevels())
        .containsExactly(AnthropicEffort.LOW, AnthropicEffort.HIGH);
    assertThat(caps.core().contextWindow()).isEqualTo(200000);
    assertThat(caps.core().maxOutputTokens()).isEqualTo(8192);
  }

  @Test
  void reportsSupportsReasoningFalseWhenReasoningAbsent() {
    final var caps = new AnthropicModelCapabilities(CORE, null);

    assertThat(caps.supportsReasoning()).isFalse();
    assertThat(caps.reasoning()).isNull();
  }
}
