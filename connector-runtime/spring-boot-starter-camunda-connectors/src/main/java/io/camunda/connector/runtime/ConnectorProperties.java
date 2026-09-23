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

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration properties for Camunda Connectors. */
@ConfigurationProperties(prefix = "camunda.connector")
public record ConnectorProperties(
    Polling polling,
    Webhook webhook,
    SecretProvider secretProvider,
    VirtualThreads virtualThreads,
    Inbound inbound,
    OAuth oauth,
    Validation validation) {
  // NOTE: this class is not used in directly in the code, but is used by Spring Boot
  // configuration annotation processor to generate the configuration properties metadata

  /**
   * Configuration for inbound webhooks.
   *
   * @param enabled whether webhook endpoints are enabled
   * @param appendPhysicalTenantAndTenantToPath whether webhook paths include the physical tenant
   *     and tenant
   * @param maxRequestBodyBytes maximum raw body size; {@code multipart/form-data} requests instead
   *     use {@code spring.servlet.multipart.max-file-size} and {@code
   *     spring.servlet.multipart.max-request-size}
   * @param rateLimit global request rate limiting shared by all webhook paths; servlet multipart
   *     parsing can occur before this limit is evaluated
   */
  public record Webhook(
      boolean enabled,
      boolean appendPhysicalTenantAndTenantToPath,
      int maxRequestBodyBytes,
      RateLimit rateLimit) {

    public Webhook(boolean enabled, boolean appendPhysicalTenantAndTenantToPath) {
      this(
          enabled,
          appendPhysicalTenantAndTenantToPath,
          10 * 1024 * 1024,
          new RateLimit(true, 1000));
    }
  }

  public record RateLimit(boolean enabled, double permitsPerSecond) {}

  /** Configuration for Operate polling that enables inbound Connectors. */
  public record Polling(
      boolean enabled, boolean activeVersionsEnabled, long interval, long initialDelay) {}

  public record VirtualThreads(boolean enabled) {}

  public record SecretProvider(
      Discovery discovery, Environment environment, ConsoleSecretProvider console) {}

  /** Configuration for the secret provider lookup */
  public record Discovery(boolean enabled) {}

  /**
   * Configuration for the {@link org.springframework.core.env.Environment} based secret provider
   */
  public record Environment(
      boolean enabled, String prefix, boolean tenantAware, boolean processDefinitionAware) {}

  public record ConsoleSecretProvider(boolean enabled, String endpoint, String audience) {}

  /** Configuration for inbound connector processing. */
  public record Inbound(ProcessDefinitionCache processDefinitionCache) {}

  /**
   * Configuration for the process definition cache used when parsing inbound connector elements.
   *
   * @param maxSize Maximum number of process definitions to cache. Default is 1000.
   */
  public record ProcessDefinitionCache(int maxSize) {
    public static final int DEFAULT_MAX_SIZE = 1000;

    public ProcessDefinitionCache {
      if (maxSize <= 0) {
        maxSize = DEFAULT_MAX_SIZE;
      }
    }

    public ProcessDefinitionCache() {
      this(DEFAULT_MAX_SIZE);
    }
  }

  /** Configuration for OAuth token caching. */
  public record OAuth(OAuthCache cache) {}

  /**
   * Configuration for the OAuth token cache.
   *
   * @param skewBuffer Buffer subtracted from the token-derived TTL to account for clock skew.
   *     Default is 10 seconds.
   */
  public record OAuthCache(Duration skewBuffer) {}

  /**
   * Configuration for custom validations that are managed by the runtime.
   *
   * @param hosts
   */
  public record Validation(Hosts hosts) {

    /**
     * Used by the {@link io.camunda.connector.hostvalidator.VerifiedHostValidator} to verify hosts
     * used by Connectors as an input configuration.
     *
     * @param enabled
     * @param allowRanges
     * @param denyRanges
     * @param unsafeAllowPrivateRanges
     * @param unsafeAllowLoopback
     */
    public record Hosts(
        boolean enabled,
        List<String> allowRanges,
        List<String> denyRanges,
        boolean unsafeAllowPrivateRanges,
        boolean unsafeAllowLoopback) {}
  }
}
