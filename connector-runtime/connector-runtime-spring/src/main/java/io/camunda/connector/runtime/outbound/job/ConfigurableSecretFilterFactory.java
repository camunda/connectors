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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
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
            // realCause walks to the root of the chain, not just one level: e is usually a
            // Cache.ValueRetrievalException (Spring's Cache#get(key, loader) wraps whatever the
            // loader throws), and the client can wrap the actual failure in its own generic
            // exception type on top of that -- a single getCause() can still return a
            // non-discriminating wrapper class. Never log the cause's message, or the cause
            // itself, anywhere -- not the incident, not the pod log -- a client/parser exception
            // message can echo response-body content, so neither sink gets more than the
            // exception's class name. The element ID, process-definition key, and exception class
            // are enough for an operator to distinguish failure modes.
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
    // Java permits a cyclic cause chain (a.initCause(b) after b was already given cause a), so
    // this must not walk unboundedly -- track visited throwables by identity and stop the moment
    // one repeats, rather than hang the worker on a malformed third-party exception. Identity,
    // not equals/hashCode: a third-party Throwable may override equality, and two distinct equal
    // causes would then stop the walk early and report a wrapper instead of the root cause.
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    Throwable current = t;
    while (current.getCause() != null && seen.add(current)) {
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
