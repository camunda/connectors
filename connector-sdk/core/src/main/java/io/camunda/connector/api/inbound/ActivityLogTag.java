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

/**
 * Common tags that can be used in activity logs when logging activities inside an inbound
 * connector.
 */
public enum ActivityLogTag {

  /** A log tag for entries related to the lifecycle of the event consumer (managed by the */
  CONSUMER("Consumer"),
  /**
   * A log tag for entries related to message/event processing. For example, use this tag to log
   * that an incoming event was processed by the connector.
   */
  MESSAGE("Message"),

  CORRELATION("Correlation"),

  LIFECYCLE("Lifecycle");

  private final String tag;

  ActivityLogTag(String tag) {
    this.tag = tag;
  }

  /**
   * Returns the string representation of the tag.
   *
   * @return the tag as a string
   */
  public String getTag() {
    return tag;
  }
}
