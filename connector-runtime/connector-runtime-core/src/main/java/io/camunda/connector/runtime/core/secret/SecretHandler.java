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

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves both forms of secret in a connector's input, in this order: {@code
 * camunda.secrets.<name>} first (new form, see {@link SecretReferenceUtil}), then {@code
 * {{secrets.X}}} / bare {@code secrets.X} (legacy form, see {@link SecretUtil}). The two are
 * unrelated mechanisms; neither falls back to the other.
 */
public class SecretHandler {

  private static final Logger LOG = LoggerFactory.getLogger(SecretHandler.class);

  protected final SecretFilter secretFilter;
  protected final SecretReferenceResolver referenceResolver;

  /** Legacy-only: looks up a bare secret name via the configured {@link SecretProvider}. */
  protected SecretReplacer legacySecretReplacer;

  /**
   * Kept so callers that only pass two arguments keep compiling. Defaults the {@code
   * camunda.secrets.<name>} resolver to {@link SecretReferenceResolver#noop()}; the outbound job
   * path uses this overload, so it is unaffected by this change.
   */
  public SecretHandler(final SecretProvider secretProvider, SecretFilter secretFilter) {
    this(secretProvider, secretFilter, SecretReferenceResolver.noop());
  }

  public SecretHandler(
      final SecretProvider secretProvider,
      SecretFilter secretFilter,
      SecretReferenceResolver referenceResolver) {
    this.secretFilter = secretFilter;
    this.referenceResolver = referenceResolver;
    legacySecretReplacer =
        (name, context) -> {
          if (secretFilter.isAllowed(name)) {
            return Optional.ofNullable(secretProvider.getSecret(name, context))
                .orElseThrow(
                    () ->
                        new ConnectorInputException(
                            String.format("Secret with name '%s' is not available", name)));
          }
          LOG.debug("Secret '{}' not in allow-list — placeholder left unreplaced", name);
          return null;
        };
  }

  public String replaceSecrets(String input, SecretContext context) {
    var withReferencesResolved = replaceCamundaSecretReferences(input);
    return SecretUtil.replaceSecrets(withReferencesResolved, context, legacySecretReplacer);
  }

  /**
   * Resolves every {@code camunda.secrets.<name>} reference, if any. A request with none never
   * calls {@link #referenceResolver}, which is what keeps this free for connectors that only use
   * the legacy form.
   */
  private String replaceCamundaSecretReferences(String input) {
    var references = SecretReferenceUtil.findReferences(input);
    if (references.isEmpty()) {
      return input;
    }
    // The filter is keyed by the bare secret name (what the outbound allow-list is built from),
    // never by the whole reference.
    Set<String> refused =
        references.stream()
            .filter(reference -> !secretFilter.isAllowed(SecretReferenceUtil.bareName(reference)))
            .collect(Collectors.toSet());
    var requested = references.stream().filter(reference -> !refused.contains(reference)).toList();
    var resolved = referenceResolver.resolve(requested);
    return SecretReferenceUtil.replaceReferences(input, resolved, refused);
  }
}
