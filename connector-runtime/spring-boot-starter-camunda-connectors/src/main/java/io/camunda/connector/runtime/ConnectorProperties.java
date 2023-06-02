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
package io.camunda.connector.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration properties for Camunda Connectors. */
@ConfigurationProperties(prefix = "camunda.connector")
public record ConnectorProperties(Polling polling, Webhook webhook) {
  // NOTE: this class is not used in directly in the code, but is used by Spring Boot
  // configuration annotation processor to generate the configuration properties metadata

  /** Configuration for the inbound webhook connector. */
  public record Webhook(boolean enabled) {}

  /** Configuration for Operate polling that enables inbound Connectors. */
  public record Polling(boolean enabled, long interval) {}
}
