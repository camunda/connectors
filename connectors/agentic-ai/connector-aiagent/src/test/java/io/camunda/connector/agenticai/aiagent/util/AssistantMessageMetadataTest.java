/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssistantMessageMetadataTest {

  @Test
  void addsTimestampToEmptyProviderMetadata() {
    final var merged = AssistantMessageMetadata.withDefaults(Map.of());

    assertThat(merged).containsOnlyKeys(AssistantMessageMetadata.TIMESTAMP_KEY);
    assertThat(merged.get(AssistantMessageMetadata.TIMESTAMP_KEY))
        .isInstanceOf(ZonedDateTime.class);
  }

  @Test
  void mergesTimestampAlongsideProviderMetadata() {
    final var providerMetadata = Map.of("anthropic", Map.of("stopReason", "end_turn"));

    final var merged = AssistantMessageMetadata.withDefaults(providerMetadata);

    assertThat(merged)
        .containsKey(AssistantMessageMetadata.TIMESTAMP_KEY)
        .containsEntry("anthropic", Map.of("stopReason", "end_turn"));
  }

  @Test
  void providerMetadataTakesPrecedenceOverDefaultsOnKeyCollision() {
    final var providerMetadata =
        Map.of(AssistantMessageMetadata.TIMESTAMP_KEY, "provider-supplied");

    final var merged = AssistantMessageMetadata.withDefaults(providerMetadata);

    assertThat(merged).containsEntry(AssistantMessageMetadata.TIMESTAMP_KEY, "provider-supplied");
  }
}
