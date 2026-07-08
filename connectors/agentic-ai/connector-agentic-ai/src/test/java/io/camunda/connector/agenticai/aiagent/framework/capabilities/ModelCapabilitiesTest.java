/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.capabilities;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities.Modality;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelCapabilitiesTest {

  @Test
  void constructsWithNullContextWindowAndMaxOutputTokens() {
    final var capabilities =
        new ModelCapabilities(
            List.of(Modality.TEXT, Modality.IMAGE),
            List.of(Modality.TEXT),
            List.of(Modality.TEXT),
            true,
            true,
            false,
            false,
            null,
            null);

    assertThat(capabilities.userMessageModalities()).containsExactly(Modality.TEXT, Modality.IMAGE);
    assertThat(capabilities.toolResultModalities()).containsExactly(Modality.TEXT);
    assertThat(capabilities.assistantMessageModalities()).containsExactly(Modality.TEXT);
    assertThat(capabilities.supportsReasoning()).isTrue();
    assertThat(capabilities.supportsReasoningSignatureRoundtrip()).isTrue();
    assertThat(capabilities.supportsPromptCaching()).isFalse();
    assertThat(capabilities.supportsParallelToolCalls()).isFalse();
    assertThat(capabilities.contextWindow()).isNull();
    assertThat(capabilities.maxOutputTokens()).isNull();
  }

  @Test
  void modalityEnumValuesAreOrderedTextImageDocumentAudioVideo() {
    assertThat(Modality.values())
        .containsExactly(
            Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT, Modality.AUDIO, Modality.VIDEO);
  }
}
