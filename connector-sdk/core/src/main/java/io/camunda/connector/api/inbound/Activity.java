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
import java.util.Objects;

// We avoid using a record here to ensure backward compatibility when adding new fields
public class Activity {
    private final Severity severity;
    private final String tag;
    private final OffsetDateTime timestamp;
    private final String message;
    private final Map<String, Object> data;

  public Activity(Severity severity, String tag, OffsetDateTime timestamp, String message,
      Map<String, Object> data) {
    this.severity = severity;
    this.tag = tag;
    this.timestamp = timestamp;
    this.message = message;
    this.data = data;
  }

  public Severity severity() {
    return severity;
  }

  public String tag() {
    return tag;
  }

  public OffsetDateTime timestamp() {
    return timestamp;
  }

  public String message() {
    return message;
  }

  public Map<String, Object> data() {
    return data;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Activity activity = (Activity) o;
    return severity == activity.severity && Objects.equals(tag, activity.tag)
        && Objects.equals(timestamp, activity.timestamp) && Objects.equals(
        message, activity.message) && Objects.equals(data, activity.data);
  }

  @Override
  public int hashCode() {
    return Objects.hash(severity, tag, timestamp, message, data);
  }

  @Override
  public String toString() {
    return "Activity{" +
        "severity=" + severity +
        ", tag='" + tag + '\'' +
        ", timestamp=" + timestamp +
        ", message='" + message + '\'' +
        ", data=" + data +
        '}';
  }

  public static BuilderStep level(Severity severity) {
    return new BuilderStep(severity);
  }

  // Before builder
  public static final class BuilderStep {

    private final Severity severity;

    private BuilderStep(Severity severity) {
      this.severity = severity;
    }

    public Builder tag(String tag) {
      return new Builder(this.severity, tag);
    }
  }

  // todo: add a real builder
  // (not a real) Builder
  @Deprecated
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
      return new Activity(severity, tag, timestamp, message, data);
    }

    public Activity messageWithException(String message, Throwable exception) {
      return new Activity(severity, tag, timestamp, buildMessage(message, exception), data);
    }

    public Activity messageWithData(String message, Map<String, Object> data) {
      return new Activity(severity, tag, timestamp, message, data);
    }

    public Activity messageWithExceptionAndData(
        String message, Throwable exception, Map<String, Object> data) {
      return new Activity(severity, tag, timestamp, buildMessage(message, exception), data);
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
