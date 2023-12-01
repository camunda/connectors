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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public class Health {

  private final Status status;
  private final Error error;

  private final Map<String, Object> details;

  private record Error(String code, String message) {}

  public enum Status {
    UP,
    UNKNOWN,
    DOWN
  }

  public Status getStatus() {
    return status;
  }

  public Map<String, Object> getDetails() {
    return details;
  }

  public Error getError() {
    return error;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Health health = (Health) o;
    return status == health.status
        && Objects.equals(error, health.error)
        && Objects.equals(details, health.details);
  }

  @Override
  public int hashCode() {
    return Objects.hash(status, error, details);
  }

  @Override
  public String toString() {
    return "Health{" + "status=" + status + ", error=" + error + ", details=" + details + '}';
  }

  static DetailsStep status(Status status) {
    return new Builder(status, null);
  }

  public static Health up() {
    return new Health(Status.UP, null, null);
  }

  public static Health up(String key, Object value) {
    return Health.status(Status.UP).detail(key, value);
  }

  public static Health up(Map<String, Object> details) {
    return new Health(Status.UP, null, details);
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

  public static Health down(String message) {
    return new Health(Status.DOWN, new Error(null, message), null);
  }

  public static Health down(String key, Object value) {
    return Health.status(Status.DOWN).detail(key, value);
  }

  public static Health down(String code, String message, Map<String, Object> details) {
    return new Health(Status.DOWN, new Error(code, message), details);
  }

  public static Health down(String message, Map<String, Object> details) {
    return new Health(Status.DOWN, new Error(null, message), details);
  }

  public static Health down(Map<String, Object> details) {
    return Health.status(Status.DOWN).details(details);
  }

  public static Health down(String code, Throwable ex) {
    return new Health(Status.DOWN, new Error(code, ex.toString()), null);
  }

  public static Health down(Throwable ex) {
    return new Health(Status.DOWN, new Error(ex.getClass().getSimpleName(), ex.toString()), null);
  }

  interface DetailsStep {
    Health detail(String key, Object value);

    Health details(Map<String, Object> details);
  }

  public static class Builder implements DetailsStep {
    private final Health.Status status;
    private Map<String, Object> details;
    private Error error;

    private Builder(Status status, Error error) {
      this.status = status;
      this.error = error;
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
    this.error = builder.error;
    this.details = builder.details;
  }

  private Health(Status status, Error error, Map<String, Object> details) {
    this.status = status;
    this.error = error;
    this.details = details;
  }
}
