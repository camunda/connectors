/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class FeelOffsetDateTimeParserTest {

  @Test
  void parsesTheBracketedZoneForFeelNowProducesOnARealBroker() {
    // FEEL's now() resolves through ZeebeFeelEngineClock, which always carries a named zone
    // region, never a bare offset; the serialized form is offset + bracketed zone id, which
    // bare OffsetDateTime.parse(text) cannot handle
    var parsed = FeelOffsetDateTimeParser.parse("2026-07-02T11:55:00.522622+02:00[Europe/Berlin]");

    assertThat(parsed).isEqualTo(OffsetDateTime.parse("2026-07-02T11:55:00.522622+02:00"));
  }

  @Test
  void parsesThePlainOffsetFormWithoutBrackets() {
    var parsed = FeelOffsetDateTimeParser.parse("2026-07-02T11:55:00.522622+02:00");

    assertThat(parsed).isEqualTo(OffsetDateTime.parse("2026-07-02T11:55:00.522622+02:00"));
  }

  @Test
  void formatsAsThePlainOffsetFormWithoutAZoneId() {
    var value = OffsetDateTime.parse("2026-07-02T11:55:00.522622+02:00");

    assertThat(FeelOffsetDateTimeParser.format(value))
        .isEqualTo("2026-07-02T11:55:00.522622+02:00");
  }

  @Test
  void roundTripsThroughFormatAndParse() {
    var value = OffsetDateTime.parse("2026-07-02T11:55:00.522622+02:00");

    assertThat(FeelOffsetDateTimeParser.parse(FeelOffsetDateTimeParser.format(value)))
        .isEqualTo(value);
  }
}
