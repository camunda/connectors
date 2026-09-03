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
package io.camunda.connector.runtime.core.secret;

import io.camunda.connector.api.secret.SecretProvider;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecretHandler {

  private static final Logger LOG = LoggerFactory.getLogger(SecretHandler.class);

  protected final SecretProvider secretProvider;

  protected Function<String, String> secretReplacer;

  // values this instance substituted, so a caller can redact against a rotated re-read
  private final Set<String> resolvedValues = ConcurrentHashMap.newKeySet();

  public SecretHandler(final SecretProvider secretProvider, SecretFilter secretFilter) {
    this.secretProvider = secretProvider;
    secretReplacer =
        name -> {
          if (secretFilter.isAllowed(name)) {
            var value =
                Optional.ofNullable(secretProvider.getSecret(name))
                    .orElseThrow(() -> new SecretNotAvailableException(name));
            resolvedValues.add(value);
            // a message that re-serializes the input carries this form, not the raw value
            resolvedValues.add(SecretUtil.jsonEscape(value));
            return value;
          }
          LOG.debug("Secret '{}' not in allow-list — placeholder left unreplaced", name);
          return null;
        };
  }

  public String replaceSecrets(String input) {
    return SecretUtil.replaceSecrets(input, secretReplacer);
  }

  public List<String> getResolvedValues() {
    return List.copyOf(resolvedValues);
  }
}
