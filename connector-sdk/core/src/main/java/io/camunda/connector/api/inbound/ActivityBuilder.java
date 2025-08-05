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
import java.util.HashMap;
import java.util.Map;
import org.apache.hc.core5.util.TextUtils;

/** Builder for creating {@link Activity} instances. */
public class ActivityBuilder {

  private Severity severity = Severity.INFO;
  private String tag;
  private String message;
  private Map<String, Object> data;

  private Health health;

  ActivityBuilder() {}

  /** Sets the severity of the activity. If not set, defaults to {@link Severity#INFO}. */
  public ActivityBuilder withSeverity(Severity severity) {
    this.severity = severity;
    return this;
  }

  /**
   * Sets a tag for the activity. Tags are not mandatory, but strongly recommended to provide a
   * better user experience in the UI. If not set, the activity will not have a tag.
   */
  public ActivityBuilder withTag(ActivityLogTag tag) {
    this.tag = tag.getTag();
    return this;
  }

  /**
   * Sets a custom tag value for the activity. Before using this method, consider whether the
   * predefined tags in {@link ActivityLogTag} are sufficient.
   */
  public ActivityBuilder withCustomTag(String tag) {
    this.tag = tag;
    return this;
  }

  public ActivityBuilder withData(Map<String, Object> data) {
    if (this.data == null) {
      this.data = data;
    } else {
      this.data.putAll(data);
    }
    return this;
  }

  public ActivityBuilder withData(String key, Object value) {
    if (this.data == null) {
      this.data = new HashMap<>();
      data.put(key, value);
    } else {
      this.data.put(key, value);
    }
    return this;
  }

  public ActivityBuilder withMessage(String message) {
    this.message = message;
    return this;
  }

  public ActivityBuilder withMessage(Throwable exception) {
    this.message = buildMessageWithException(null, exception);
    return this;
  }

  public ActivityBuilder withMessage(String message, Throwable exception) {
    this.message = buildMessageWithException(message, exception);
    return this;
  }

  /**
   * If new health is provided, it will be updated along with adding the activity to the log. This
   * method is useful when both health update and activity logging are needed.
   */
  public ActivityBuilder andReportHealth(Health health) {
    this.health = health;
    return this;
  }

  /**
   * Builds the activity instance with the provided properties.
   *
   * @return a new {@link Activity} instance
   */
  public Activity build() {
    if (TextUtils.isEmpty(tag)
        && TextUtils.isEmpty(message)
        && (data == null || data.isEmpty())
        && health == null) {
      throw new IllegalArgumentException(
          "Activity contains no data. At least one of tag, message, data, or health must be set.");
    }
    return new Activity(severity, tag, OffsetDateTime.now(), message, data, health);
  }

  static String buildMessageWithException(String message, Throwable exception) {
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
