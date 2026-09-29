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
public record ConnectorProperties(Polling polling, Webhook webhook, SecretProvider secretProvider) {
  // NOTE: this class is not used in directly in the code, but is used by Spring Boot
  // configuration annotation processor to generate the configuration properties metadata

  /**
   * Configuration for inbound webhooks.
   *
   * @param enabled whether webhook endpoints are enabled
   * @param maxRequestBodyBytes maximum raw body size; {@code multipart/form-data} requests instead
   *     use {@code spring.servlet.multipart.max-file-size} and {@code
   *     spring.servlet.multipart.max-request-size}
   * @param rateLimit global request rate limiting shared by all registered webhook paths; servlet
   *     multipart parsing can occur before this limit is evaluated
   */
  public record Webhook(boolean enabled, int maxRequestBodyBytes, RateLimit rateLimit) {

    public Webhook(boolean enabled) {
      this(enabled, 10 * 1024 * 1024, new RateLimit(true, 1000));
    }
  }

  public record RateLimit(boolean enabled, double permitsPerSecond) {}

  /** Configuration for Operate polling that enables inbound Connectors. */
  public record Polling(boolean enabled, long interval) {}

  public record SecretProvider(
      Discovery discovery, Environment environment, ConsoleSecretProvider console) {}

  /** Configuration for the secret provider lookup */
  public record Discovery(boolean enabled) {}

  /**
   * Configuration for the {@link org.springframework.core.env.Environment} based secret provider
   */
  public record Environment(boolean enabled, String prefix) {}

  public record ConsoleSecretProvider(boolean enabled, String endpoint, String audience) {}
}
