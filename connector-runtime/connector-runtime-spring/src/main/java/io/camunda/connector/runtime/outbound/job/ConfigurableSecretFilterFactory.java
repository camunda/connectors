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
import io.camunda.connector.runtime.outbound.secret.SecretFilterUnavailableException;
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
          } catch (SecretFilterUnavailableException e) {
            // Self-authored operator guidance, never derived from a client or parser response --
            // unlike every other failure below, its message is safe to surface as-is.
            if (strict) {
              LOG.error(
                  "Secret filter unavailable for element '{}' in process definition key {}: {}",
                  context.elementId(),
                  context.processDefinitionKey(),
                  e.getMessage());
              throw new IllegalArgumentException(e.getMessage());
            } else {
              LOG.warn(
                  "Secret filter unavailable for element '{}' in process definition key {}: {}, will allow all as secret-filter-mode is LAX",
                  context.elementId(),
                  context.processDefinitionKey(),
                  e.getMessage());
              return null;
            }
          } catch (RuntimeException e) {
            // realCause walks to the root of the chain, not just one level: the Operate SDK's
            // HTTP layer wraps the actual failure in its own generic exception type before it
            // ever reaches here, so a single getCause() still returns a non-discriminating class.
            // Never log the cause's message, or the cause itself, anywhere -- not the incident,
            // not the pod log -- a client/parser exception message can echo response-body
            // content, so neither sink gets more than the exception's class name. The element ID,
            // process-definition key, and exception class are enough for an operator to
            // distinguish failure modes.
            Throwable realCause = mostSpecificCause(e);
            if (strict) {
              LOG.error(
                  "Error retrieving secret keys for element '{}' in process definition key {} ({})",
                  context.elementId(),
                  context.processDefinitionKey(),
                  realCause.getClass().getName());
              throw new IllegalArgumentException(
                  "Error retrieving secret keys for element '"
                      + context.elementId()
                      + "' in process definition key "
                      + context.processDefinitionKey()
                      + " ("
                      + realCause.getClass().getName()
                      + ")");
            } else {
              LOG.warn(
                  "Error filtering secrets for element '{}' in process definition key {} ({}), will allow all as secret-filter-mode is LAX",
                  context.elementId(),
                  context.processDefinitionKey(),
                  realCause.getClass().getName());
              return null;
            }
          }
        });
  }

  private static Throwable mostSpecificCause(Throwable t) {
    Throwable current = t;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  public enum SecretFilterMode {
    DISABLED,
    LAX,
    STRICT
  }
}
