/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.message.StopReason.KnownStopReason;
import io.camunda.connector.agenticai.aiagent.model.message.StopReason.UnknownStopReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class StopReasonTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @ParameterizedTest
  @EnumSource(KnownStopReason.class)
  void knownValuesSerializeToTheSameBareStringAsTheLegacyEnum(KnownStopReason knownStopReason)
      throws Exception {
    final String json = mapper.writeValueAsString((StopReason) knownStopReason);

    // identical to what Enum#name() based serialization produced before StopReason became a
    // sealed interface -- a bare JSON string equal to the constant's name, not an object.
    assertThat(json).isEqualTo("\"%s\"".formatted(knownStopReason.name()));
  }

  @ParameterizedTest
  @EnumSource(KnownStopReason.class)
  void knownValuesRoundTrip(KnownStopReason knownStopReason) throws Exception {
    final String json = mapper.writeValueAsString((StopReason) knownStopReason);
    final StopReason restored = mapper.readValue(json, StopReason.class);

    assertThat(restored).isSameAs(knownStopReason);
  }

  @Test
  void legacyPersistedStringDeserializesToTheKnownConstant() throws Exception {
    final StopReason restored = mapper.readValue("\"TOOL_USE\"", StopReason.class);

    assertThat(restored).isSameAs(StopReason.TOOL_USE);
  }

  @Test
  void unrecognisedValueDeserializesToUnknownStopReasonCarryingTheRawString() throws Exception {
    final StopReason restored = mapper.readValue("\"some_new_vendor_reason\"", StopReason.class);

    assertThat(restored).isInstanceOf(UnknownStopReason.class);
    assertThat(restored.value()).isEqualTo("some_new_vendor_reason");
  }

  @Test
  void unknownStopReasonRoundTripsToTheSameRawString() throws Exception {
    final StopReason unknown = new UnknownStopReason("some_new_vendor_reason");

    final String json = mapper.writeValueAsString(unknown);
    assertThat(json).isEqualTo("\"some_new_vendor_reason\"");

    final StopReason restored = mapper.readValue(json, StopReason.class);
    assertThat(restored).isEqualTo(unknown);
  }

  @Test
  void reExposedInterfaceConstantsMatchKnownStopReasonConstants() {
    assertThat(StopReason.STOP).isSameAs(KnownStopReason.STOP);
    assertThat(StopReason.LENGTH).isSameAs(KnownStopReason.LENGTH);
    assertThat(StopReason.TOOL_USE).isSameAs(KnownStopReason.TOOL_USE);
    assertThat(StopReason.CONTENT_FILTERED).isSameAs(KnownStopReason.CONTENT_FILTERED);
    assertThat(StopReason.GUARDRAIL).isSameAs(KnownStopReason.GUARDRAIL);
    assertThat(StopReason.ERROR).isSameAs(KnownStopReason.ERROR);
    assertThat(StopReason.ABORTED).isSameAs(KnownStopReason.ABORTED);
    assertThat(StopReason.UNKNOWN).isSameAs(KnownStopReason.UNKNOWN);
  }
}
