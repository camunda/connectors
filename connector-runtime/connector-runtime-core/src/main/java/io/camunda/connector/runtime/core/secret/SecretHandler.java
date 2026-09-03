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

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecretHandler {

  private static final Logger LOG = LoggerFactory.getLogger(SecretHandler.class);

  protected final SecretProvider secretProvider;

  protected SecretReplacer secretReplacer;

  // values this instance substituted, so a caller can redact against a rotated re-read
  private final Set<String> resolvedValues = ConcurrentHashMap.newKeySet();

  public SecretHandler(final SecretProvider secretProvider, SecretFilter secretFilter) {
    this.secretProvider = secretProvider;
    secretReplacer =
        (secretFilterContext, context) -> {
          if (secretFilter.isAllowed(secretFilterContext)) {
            var value =
                Optional.ofNullable(
                        secretProvider.getSecret(secretFilterContext.secretName(), context))
                    .orElseThrow(
                        () ->
                            new ConnectorInputException(
                                String.format(
                                    "Secret with name '%s' is not available",
                                    secretFilterContext.secretName()),
                                null));
            resolvedValues.add(value);
            // the serialized job JSON carries this form, not the raw value, when it differs
            resolvedValues.add(SecretUtil.jsonEscape(value));
            return value;
          }
          LOG.debug(
              "Secret '{}' not in allow-list — placeholder left unreplaced",
              secretFilterContext.secretName());
          return null;
        };
  }

  public JsonNode replaceSecrets(JsonNode input, SecretContext context) {
    return SecretUtil.replaceSecrets(input, context, secretReplacer);
  }

  public List<String> getResolvedValues() {
    return List.copyOf(resolvedValues);
  }
}
