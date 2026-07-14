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
  void modalityEnumValuesAreOrderedTextImageDocumentAudioVideo() {
    assertThat(Modality.values())
        .containsExactly(
            Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT, Modality.AUDIO, Modality.VIDEO);
  }

  @Test
  void coreModelCapabilitiesExposesModalitiesAndLimits() {
    final ModelCapabilities caps =
        new CoreModelCapabilities(
            List.of(Modality.TEXT, Modality.IMAGE),
            List.of(Modality.TEXT),
            List.of(Modality.TEXT),
            128000,
            4096);

    assertThat(caps.userMessageModalities()).containsExactly(Modality.TEXT, Modality.IMAGE);
    assertThat(caps.toolResultModalities()).containsExactly(Modality.TEXT);
    assertThat(caps.assistantMessageModalities()).containsExactly(Modality.TEXT);
    assertThat(((CoreModelCapabilities) caps).contextWindow()).isEqualTo(128000);
    assertThat(((CoreModelCapabilities) caps).maxOutputTokens()).isEqualTo(4096);
  }

  @Test
  void coreModelCapabilitiesAllowsNullLimits() {
    final var caps =
        new CoreModelCapabilities(
            List.of(Modality.TEXT), List.of(Modality.TEXT), List.of(Modality.TEXT), null, null);

    assertThat(caps.contextWindow()).isNull();
    assertThat(caps.maxOutputTokens()).isNull();
  }
}
