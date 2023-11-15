/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.api.inbound;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Health {

  private final Status status;
  private final Map<String, Object> details;

  private HealthError error;

  public enum Status {
    UP,
    UNKNOWN,
    DOWN
  }

  public enum ReservedDetailKeyword {
    ERROR("error"),
    PATH("path");

    private final String value;

    ReservedDetailKeyword(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  public Status getStatus() {
    return status;
  }

  public Map<String, Object> getDetails() {
    return details;
  }

  public HealthError getError() {
    return error;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Health health = (Health) o;
    return status == health.status
        && Objects.equals(details, health.details)
        && Objects.equals(error, health.error);
  }

  @Override
  public int hashCode() {
    return Objects.hash(status, details);
  }

  @Override
  public String toString() {
    return "Health{" +
            "status=" + status +
            ", details=" + details +
            ", error=" + error +
            '}';
  }

  static DetailsStep status(Status status) {
    return new Builder(status);
  }

  public Health error(final HealthError error) {
    this.error = error;
    return this;
  }

  public static Health up() {
    return new Health(Status.UP, null, null);
  }

  public static Health up(String key, Object value) {
    return Health.status(Status.UP).detail(key, value);
  }

  public static Health up(Map<String, Object> details) {
    return new Health(Status.UP, details, null);
  }

  public static Health unknown() {
    return new Health(Status.UNKNOWN, null, null);
  }

  public static Health unknown(String key, String value) {
    return Health.status(Status.UNKNOWN).detail(key, value);
  }

  public static Health unknown(Map<String, Object> details) {
    return Health.status(Status.UNKNOWN).details(details);
  }

  public static Health down() {
    return new Health(Status.DOWN, null, null);
  }

  public static Health down(String key, Object value) {
    return Health.status(Status.DOWN).detail(key, value);
  }

  public static Health down(Map<String, Object> details) {
    return Health.status(Status.DOWN).details(details);
  }

  public static Health down(Throwable ex) {
    String error = ex.getClass().getName() + ": " + ex.getMessage();
    return Health.status(Status.DOWN).detail("error", error);
  }

  public static Health down(Throwable ex, String title) {
    HealthError healthError =
        new HealthError(HealthErrorSeverity.ERROR, ex.getMessage(), getStackTrace(ex), title);
    return Health.status(Status.DOWN).error(healthError);
  }

  public Health merge(Health newHealth) {
    Map<String, Object> mergedDetails =
        this.getDetails() != null ? this.getDetails() : new HashMap<>();
    if (newHealth.getDetails() != null && !newHealth.getDetails().isEmpty()) {
      mergedDetails.putAll(newHealth.getDetails());
    }
    return Health.status(newHealth.getStatus()).details(mergedDetails).error(newHealth.getError());
  }

  public static String getStackTrace(Throwable e) {
    StringWriter sw = new StringWriter();
    e.printStackTrace(new PrintWriter(sw));
    return sw.toString();
  }

  interface DetailsStep {
    Health detail(String key, Object value);

    Health details(Map<String, Object> details);

    Health error(HealthError error);
  }

  public static class Builder implements DetailsStep {
    private final Health.Status status;
    private Map<String, Object> details;

    private HealthError error;

    Builder(Status status) {
      this.status = status;
    }

    @Override
    public Health error(HealthError error) {
      this.error = error;
      return new Health(this);
    }

    @Override
    public Health detail(String key, Object value) {
      this.details = Collections.singletonMap(key, value);
      return new Health(this);
    }

    @Override
    public Health details(Map<String, Object> details) {
      this.details = details;
      return new Health(this);
    }
  }

  private Health(Builder builder) {
    this.status = builder.status;
    this.details = builder.details;
    this.error = builder.error;
  }

  private Health(Status status, Map<String, Object> details, HealthError error) {
    this.status = status;
    this.details = details;
    this.error = error;
  }
}
