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
package io.camunda.connector.runtime.secret;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.runtime.secret.console.ConsoleSecretApiClient;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Secret provider that fetches secrets from the Console Cluster API provided by the Camunda SaaS
 * Platform.
 */
public class ConsoleSecretProvider implements SecretProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleSecretProvider.class);

  private static final String CACHE_KEY = "secrets";

  private final LoadingCache<String, Map<String, String>> secretsCache;

  public ConsoleSecretProvider(
      ConsoleSecretApiClient consoleSecretApiClient, Duration cacheRefresh) {
    // We do not cache individual values as the response always contains all secrets
    secretsCache =
        CacheBuilder.newBuilder()
            .refreshAfterWrite(cacheRefresh)
            .build(
                new CacheLoader<>() {
                  @Override
                  public Map<String, String> load(String key) {
                    return consoleSecretApiClient.getSecrets();
                  }
                });
  }

  @Override
  public String getSecret(String name) {
    LOGGER.debug("Resolving secret for key: " + name);
    return secretsCache.getUnchecked(CACHE_KEY).getOrDefault(name, null);
  }
}
