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
import java.time.OffsetDateTime;
import java.util.Map;

public record Activity(
    Severity severity,
    String tag,
    OffsetDateTime timestamp,
    String message,
    Map<String, Object> data,
    Health healthChange) {

  /** Creates a new builder for an {@link Activity} instance. */
  public static ActivityBuilder newBuilder() {
    return new ActivityBuilder();
  }

  /**
   * Pseudo-builder for creating {@link Activity} instances.
   *
   * <p>Do not use this class. If you are using this class, please switch to {@link ActivityBuilder}
   * instead.
   *
   * @see Activity#newBuilder()
   */
  @Deprecated(forRemoval = true, since = "8.8")
  public static BuilderStep level(Severity severity) {
    return new BuilderStep(severity);
  }

  // Before builder
  /**
   * Pseudo-builder for creating {@link Activity} instances.
   *
   * <p>Do not use this class. If you are using this class, please switch to {@link ActivityBuilder}
   * instead.
   *
   * @see Activity#newBuilder()
   */
  @Deprecated(forRemoval = true, since = "8.8")
  public static final class BuilderStep {

    private final Severity severity;

    private BuilderStep(Severity severity) {
      this.severity = severity;
    }

    public Builder tag(String tag) {
      return new Builder(this.severity, tag);
    }
  }

  /**
   * Pseudo-builder for creating {@link Activity} instances.
   *
   * <p>Do not use this class. If you are using this class, please switch to {@link ActivityBuilder}
   * instead.
   *
   * @see Activity#newBuilder()
   */
  @Deprecated(forRemoval = true, since = "8.8")
  public static final class Builder {

    Severity severity;
    String tag;
    OffsetDateTime timestamp;
    Map<String, Object> data;

    private Builder(Severity severity, String tag) {
      this.severity = severity;
      this.tag = tag;
      this.timestamp = OffsetDateTime.now();
    }

    public Activity message(String message) {
      return new Activity(severity, tag, timestamp, message, data, null);
    }

    public Activity messageWithException(String message, Throwable exception) {
      return new Activity(severity, tag, timestamp, buildMessage(message, exception), data, null);
    }

    public Activity messageWithData(String message, Map<String, Object> data) {
      return new Activity(severity, tag, timestamp, message, data, null);
    }

    public Activity messageWithExceptionAndData(
        String message, Throwable exception, Map<String, Object> data) {
      return new Activity(severity, tag, timestamp, buildMessage(message, exception), data, null);
    }

    private String buildMessage(String message, Throwable exception) {
      if (exception == null) {
        return message;
      }
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      exception.printStackTrace(pw);
      if (message == null) {
        return sw.toString();
      }
      return message + "\n" + sw;
    }
  }
}
