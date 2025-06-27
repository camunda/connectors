/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.polling.model;

import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import java.time.Duration;
import java.util.Objects;

public class PollingIntervalInput extends HttpCommonRequest {
  private static final Duration DEFAULT_HTTP_REQUEST_INTERVAL = Duration.ofSeconds(50);
  private static final Duration DEFAULT_PROCESS_POLLING_INTERVAL = Duration.ofSeconds(5);
  @FEEL private Duration httpRequestInterval;
  @FEEL private Duration processPollingInterval;

  public Duration getHttpRequestInterval() {
    return httpRequestInterval != null ? httpRequestInterval : DEFAULT_HTTP_REQUEST_INTERVAL;
  }

  public void setHttpRequestInterval(final Duration httpRequestInterval) {
    this.httpRequestInterval = httpRequestInterval;
  }

  public Duration getProcessPollingInterval() {
    return processPollingInterval != null
        ? processPollingInterval
        : DEFAULT_PROCESS_POLLING_INTERVAL;
  }

  public void setProcessPollingInterval(final Duration processPollingInterval) {
    this.processPollingInterval = processPollingInterval;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final PollingIntervalInput that)) {
      return false;
    }
    return Objects.equals(httpRequestInterval, that.httpRequestInterval)
        && Objects.equals(processPollingInterval, that.processPollingInterval);
  }

  @Override
  public int hashCode() {
    return Objects.hash(httpRequestInterval, processPollingInterval);
  }

  @Override
  public String toString() {
    return "PollingIntervalConfiguration{"
        + "httpRequestInterval="
        + httpRequestInterval
        + ", processPollingInterval="
        + processPollingInterval
        + "}";
  }
}
