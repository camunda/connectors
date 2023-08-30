/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.polling.model;

import io.camunda.connector.api.annotation.FEEL;
import java.time.Duration;
import java.util.Objects;

public class PollingIntervalConfiguration {

  private static final Duration DEFAULT_HTTP_REQUEST_INTERVAL = Duration.ofSeconds(50);
  private static final Duration DEFAULT_OPERATE_INTERVAL = Duration.ofSeconds(5);
  @FEEL private Duration httpRequestInterval;
  @FEEL private Duration operatePollingInterval;

  public Duration getHttpRequestInterval() {
    return httpRequestInterval != null ? httpRequestInterval : DEFAULT_HTTP_REQUEST_INTERVAL;
  }

  public void setHttpRequestInterval(final Duration httpRequestInterval) {
    this.httpRequestInterval = httpRequestInterval;
  }

  public Duration getOperatePollingInterval() {
    return operatePollingInterval != null ? operatePollingInterval : DEFAULT_OPERATE_INTERVAL;
  }

  public void setOperatePollingInterval(final Duration operatePollingInterval) {
    this.operatePollingInterval = operatePollingInterval;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final PollingIntervalConfiguration that)) {
      return false;
    }
    return Objects.equals(httpRequestInterval, that.httpRequestInterval)
        && Objects.equals(operatePollingInterval, that.operatePollingInterval);
  }

  @Override
  public int hashCode() {
    return Objects.hash(httpRequestInterval, operatePollingInterval);
  }

  @Override
  public String toString() {
    return "PollingIntervalConfiguration{"
        + "httpRequestInterval="
        + httpRequestInterval
        + ", operatePollingInterval="
        + operatePollingInterval
        + "}";
  }
}
