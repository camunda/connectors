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
package io.camunda.connector.runtime.outbound.job;

import io.camunda.connector.runtime.core.secret.SecretFilter;
import io.camunda.connector.runtime.core.secret.SecretFilterFactory;
import io.camunda.connector.runtime.core.secret.SecretFilterFactory.SecretFilterContext;
import io.camunda.connector.runtime.outbound.secret.SecretKeyCache;
import io.camunda.connector.runtime.outbound.secret.SecretKeyCache.SecretKeyContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigurableSecretFilterFactory implements SecretFilterFactory {
  private static final Logger LOG = LoggerFactory.getLogger(ConfigurableSecretFilterFactory.class);
  private final SecretFilterMode secretFilterMode;
  private final SecretKeyCache secretKeyCache;

  public ConfigurableSecretFilterFactory(
      SecretFilterMode secretFilterMode, SecretKeyCache secretKeyCache) {
    this.secretFilterMode = secretFilterMode;
    this.secretKeyCache = secretKeyCache;
  }

  @Override
  public SecretFilter create(SecretFilterContext context) {
    return switch (secretFilterMode) {
      case DISABLED -> SecretFilter.allowAll();
      case LAX -> enabled(context, false);
      case STRICT -> enabled(context, true);
    };
  }

  private SecretFilter enabled(SecretFilterContext context, boolean strict) {
    return new LazyLoadingSecretFilter(
        () -> {
          try {
            return secretKeyCache.getSecretKeys(
                new SecretKeyContext(context.processDefinitionKey(), context.elementId()));
          } catch (RuntimeException e) {
            if (strict) {
              throw new IllegalArgumentException("Error retrieving secret keys", e);
            } else {
              LOG.warn(
                  "Error filtering secrets for element '{}' in process definition key {}), will allow all as secret-filter-mode is LAX",
                  context.elementId(),
                  context.processDefinitionKey(),
                  e);
              return null;
            }
          }
        });
  }

  public enum SecretFilterMode {
    DISABLED,
    LAX,
    STRICT
  }
}
