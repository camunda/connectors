/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.capabilities;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.AgenticAiCapabilitiesProperties.ApiFamilyProperties;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.AgenticAiCapabilitiesProperties.ModelEntryProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Validation guards on {@link CapabilityMatrixFactory#build(AgenticAiCapabilitiesProperties,
 * ObjectMapper)}.
 */
class CapabilityMatrixFactoryTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void rejectsEntryDeclaringBothIdAndPattern() {
    final var entry =
        new ModelEntryProperties(
            "claude-opus-4-7", List.of("claude-opus-*"), List.of(), null, null);
    final var properties =
        new AgenticAiCapabilitiesProperties(
            Map.of("anthropic-messages", new ApiFamilyProperties(null, Map.of("opus", entry))));

    assertThatIllegalStateException()
        .isThrownBy(() -> CapabilityMatrixFactory.build(properties, mapper))
        .withMessageContaining("must specify at most one of `id` or `patterns`");
  }

  @Test
  void rejectsPatternEntryDeclaringAliases() {
    final var entry =
        new ModelEntryProperties(
            null, List.of("claude-opus-*"), List.of("claude-opus-latest"), null, null);
    final var properties =
        new AgenticAiCapabilitiesProperties(
            Map.of("anthropic-messages", new ApiFamilyProperties(null, Map.of("opus", entry))));

    assertThatIllegalStateException()
        .isThrownBy(() -> CapabilityMatrixFactory.build(properties, mapper))
        .withMessageContaining("cannot declare aliases");
  }
}
