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
package io.camunda.connector.runtime.cloud;

import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.impl.config.ConnectorConfigurationUtil;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GcpSecretManagerSecretProvider implements SecretProvider {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(GcpSecretManagerSecretProvider.class);
  private final Gson gson;
  private final String clusterId;
  private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

  public static final String SECRETS_PROJECT_ENV_NAME = "SECRETS_PROJECT_ID";
  public static final String SECRETS_PREFIX_ENV_NAME = "SECRETS_PREFIX";
  public static final String CLUSTER_ID_ENV_NAME = "CAMUNDA_CLUSTER_ID";

  // private Map<String, String> secrets = new HashMap<>();
  private static final String CACHE_KEY = "SECRETS";
  LoadingCache<String, Map<String, String>> secretsCache;

  public GcpSecretManagerSecretProvider() {
    this(ConnectorConfigurationUtil.getProperty(CLUSTER_ID_ENV_NAME));
  }

  public GcpSecretManagerSecretProvider(String clusterId) {
    this(new GsonBuilder().create(), clusterId);
  }

  public GcpSecretManagerSecretProvider(Gson gson, String clusterId) {
    this.gson = gson;
    this.clusterId = clusterId;
    this.setupSecretsCache();
  }

  public void setupSecretsCache() {
    // Load secrets via this loader function whenever necessary
    CacheLoader<String, Map<String, String>> loader =
        new CacheLoader<String, Map<String, String>>() {
          @Override
          public Map<String, String> load(String key) {
            return unwrapSecrets(loadGoogleSecrets(clusterId));
          }
        };
    secretsCache = CacheBuilder.newBuilder().refreshAfterWrite(5, TimeUnit.SECONDS).build(loader);
  }

  protected Map<String, String> unwrapSecrets(final String secretsAsjson) {
    return gson.fromJson(secretsAsjson, MAP_TYPE);
  }

  protected String loadGoogleSecrets(final String clusterId) {
    Objects.requireNonNull(clusterId, "You need to specify the clusterId to load secrets for");
    LOGGER.info("Fetching secrets for cluster {} from secret manager", clusterId);
    try (final SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
      final String projectId =
          Objects.requireNonNull(
              System.getenv(SECRETS_PROJECT_ENV_NAME),
              "Environment variable " + SECRETS_PROJECT_ENV_NAME + " is missing");
      final String secretPrefix =
          Objects.requireNonNull(
              System.getenv(SECRETS_PREFIX_ENV_NAME),
              "Environment variable " + SECRETS_PREFIX_ENV_NAME + " is missing");

      final String secretName = String.format("%s-%s", secretPrefix, clusterId);
      final SecretVersionName secretVersionName =
          SecretVersionName.of(projectId, secretName, "latest");
      final AccessSecretVersionResponse response = client.accessSecretVersion(secretVersionName);
      return response.getPayload().getData().toStringUtf8();
    } catch (final Exception e) {
      LOGGER.trace("Failed to load secrets from secret manager", e);
      throw new RuntimeException("Failed to load secrets from secret manager", e);
    }
  }

  @Override
  public String getSecret(String name) {
    try {
      return secretsCache.get(CACHE_KEY).get(name);
    } catch (ExecutionException e) {
      throw new ConnectorException("Could not resolve secrets: " + e.getMessage(), e);
    }
  }
}
