/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.common.util;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Parses and formats {@link OffsetDateTime} values produced by FEEL's {@code now()} function.
 *
 * <p>On a real broker, {@code now()} resolves through {@code ZeebeFeelEngineClock} ({@code
 * clock.instant().atZone(ZoneId.systemDefault())}), and {@code ZoneId.systemDefault()} resolves to
 * a named region (e.g. {@code Etc/UTC}, {@code Europe/Berlin}) on essentially every real
 * deployment, never a bare {@code ZoneOffset}. The resulting value is serialized with an offset
 * plus a bracketed zone id (e.g. {@code 2026-07-02T11:55:00.522622+02:00[Europe/Berlin]}), which
 * bare {@link OffsetDateTime#parse(CharSequence)} — and Jackson's default {@code JavaTimeModule}
 * deserializer, which uses the same strict formatter — cannot parse.
 */
public final class FeelOffsetDateTimeParser {

  private FeelOffsetDateTimeParser() {}

  /**
   * Parses a FEEL {@code now()} string, accepting both the bracketed-zone form a real broker
   * produces and the plain offset form (no brackets) {@link #format(OffsetDateTime)} writes.
   */
  public static OffsetDateTime parse(String text) {
    return OffsetDateTime.parse(text, DateTimeFormatter.ISO_ZONED_DATE_TIME);
  }

  /**
   * Formats as the plain offset form (no zone id), e.g. {@code 2026-07-02T11:55:00.522622+02:00}.
   */
  public static String format(OffsetDateTime value) {
    return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(value);
  }
}
