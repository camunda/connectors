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
package io.camunda.connector.secret.providers;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

public abstract class AbstractSecretProvider implements SecretProvider, AutoCloseable {

  /** Secrets used as fallback if SecretProvider is loaded via SPI */
  public static final String SECRETS_PROJECT_ENV_NAME = "SECRETS_PROJECT_ID";

  public static final String SECRETS_PREFIX_ENV_NAME = "SECRETS_PREFIX";
  public static final String CLUSTER_ID_ENV_NAME = "CAMUNDA_CLUSTER_ID";
  public static final String SECRETS_CACHE_MILLIS_ENV_NAME =
      "CAMUNDA_CONNECTOR_SECRETS_CACHE_MILLIS";

  private static final Logger logger = LoggerFactory.getLogger(AbstractSecretProvider.class);
  private static final ObjectMapper DEFAULT_MAPPER =
      JsonMapper.builder()
          .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .build();
  private static final String CACHE_KEY = "SECRETS";
  private final String clusterId;
  private final String secretsProjectId;
  private final String secretsNamePrefix;
  LoadingCache<String, Map<String, String>> secretsCache;
  private ObjectMapper mapper;

  public AbstractSecretProvider() {
    this(
        System.getenv(CLUSTER_ID_ENV_NAME),
        System.getenv(SECRETS_PROJECT_ENV_NAME),
        System.getenv(SECRETS_PREFIX_ENV_NAME));
  }

  public AbstractSecretProvider(
      String clusterId, String secretsProjectId, String secretsNamePrefix) {
    this(DEFAULT_MAPPER, clusterId, secretsProjectId, secretsNamePrefix);
  }

  public AbstractSecretProvider(
      ObjectMapper mapper, String clusterId, String secretsProjectId, String secretsNamePrefix) {
    this.mapper = mapper;

    this.clusterId = clusterId;
    this.secretsProjectId = secretsProjectId;
    this.secretsNamePrefix =
        Objects.requireNonNull(
            secretsNamePrefix, "Configuration for Secrets name prefix is missing");

    this.setupSecretsCache();
  }

  public void setupSecretsCache() {
    // Load secrets via this loader function whenever necessary
    CacheLoader<String, Map<String, String>> loader =
        new CacheLoader<>() {
          @Override
          public Map<String, String> load(String key) {
            return unwrapSecrets(
                loadSecrets(clusterId, secretsProjectId, secretsNamePrefix, logger));
          }
        };
    long millis =
        Long.parseLong(
            Optional.ofNullable(System.getenv(SECRETS_CACHE_MILLIS_ENV_NAME))
                .orElseGet(() -> "5000"));
    secretsCache =
        CacheBuilder.newBuilder().refreshAfterWrite(millis, TimeUnit.MILLISECONDS).build(loader);
  }

  protected Map<String, String> unwrapSecrets(final String secretsAsJson) {
    return mapper.readValue(secretsAsJson, Map.class);
  }

  protected abstract String loadSecrets(
      String clusterId, String secretsProjectId, String secretsNamePrefix, Logger logger);

  @Override
  public String getSecret(String name, SecretContext context) {
    try {
      return secretsCache.get(CACHE_KEY).get(name);
    } catch (ExecutionException | UncheckedExecutionException e) {
      // Jackson 3 exceptions are unchecked, so Guava wraps them as UncheckedExecutionException
      // rather than ExecutionException when they escape the CacheLoader.
      throw new ConnectorException("Could not resolve secrets: " + e.getMessage(), e);
    }
  }
}
