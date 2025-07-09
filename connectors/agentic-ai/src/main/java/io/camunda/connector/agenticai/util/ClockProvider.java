/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.util;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Objects;

public class ClockProvider {
  private static Clock clock = defaultClock();

  private ClockProvider() {}

  public static Clock getClock() {
    return clock;
  }

  public static void setClock(final Clock newClock) {
    Objects.requireNonNull(newClock, "Clock cannot be null");
    clock = newClock;
  }

  public static void resetClock() {
    clock = defaultClock();
  }

  private static Clock defaultClock() {
    return Clock.systemDefaultZone();
  }

  public static ZonedDateTime zonedDateTimeNow() {
    return ZonedDateTime.now(clock);
  }
}
