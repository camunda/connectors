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

import java.time.OffsetDateTime;

public record Activity(Severity severity, String tag, OffsetDateTime timestamp, String message) {

  public static BuilderStep level(Severity severity) {
    return new BuilderStep(severity);
  }

  // Before builder
  public static final class BuilderStep {

    private Severity severity;

    private BuilderStep(Severity severity) {
      this.severity = severity;
    }

    public Builder tag(String tag) {
      return new Builder(this.severity, tag);
    }
  }

  // Builder
  public static final class Builder {

    Severity severity;
    String tag;
    OffsetDateTime timestamp;

    private Builder(Severity severity, String tag) {
      this.severity = severity;
      this.tag = tag;
      this.timestamp = OffsetDateTime.now();
    }

    public Activity message(String message) {
      return new Activity(severity, tag, timestamp, message);
    }
  }
}
