/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class FeelOffsetDateTimeParserTest {

  // statically constructed rather than parsed, so the expectation does not lean on the same
  // parsing behavior under test; .522622s == 522_622_000ns
  private static final OffsetDateTime DATE_TIME =
      OffsetDateTime.of(
          LocalDate.of(2026, 7, 2), LocalTime.of(11, 55, 0, 522_622_000), ZoneOffset.ofHours(2));

  @Test
  void parsesTheBracketedZoneForFeelNowProducesOnARealBroker() {
    // FEEL's now() resolves through ZeebeFeelEngineClock, which always carries a named zone
    // region, never a bare offset; the serialized form is offset + bracketed zone id, which
    // bare OffsetDateTime.parse(text) cannot handle
    var parsed = FeelOffsetDateTimeParser.parse("2026-07-02T11:55:00.522622+02:00[Europe/Berlin]");

    assertThat(parsed).isEqualTo(DATE_TIME);
  }

  @Test
  void parsesThePlainOffsetFormWithoutBrackets() {
    var parsed = FeelOffsetDateTimeParser.parse("2026-07-02T11:55:00.522622+02:00");

    assertThat(parsed).isEqualTo(DATE_TIME);
  }

  @Test
  void formatsAsThePlainOffsetFormWithoutAZoneId() {
    assertThat(FeelOffsetDateTimeParser.format(DATE_TIME))
        .isEqualTo("2026-07-02T11:55:00.522622+02:00");
  }

  @Test
  void roundTripsThroughFormatAndParse() {
    assertThat(FeelOffsetDateTimeParser.parse(FeelOffsetDateTimeParser.format(DATE_TIME)))
        .isEqualTo(DATE_TIME);
  }
}
