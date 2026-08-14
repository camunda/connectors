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

import com.google.common.collect.Lists;
import io.camunda.client.CamundaClient;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves {@code camunda.secrets.<name>} references against a single {@link CamundaClient}'s
 * cluster. One instance is bound to one physical tenant's client. The resolve call itself takes no
 * tenant parameter — the cluster works that out from the caller's own token.
 */
public class CamundaClientSecretResolver implements SecretReferenceResolver {

  private static final Logger LOG = LoggerFactory.getLogger(CamundaClientSecretResolver.class);

  // POST /v2/secrets/resolve rejects more than 20 references per call with HTTP 400.
  private static final int MAX_REFERENCES_PER_CALL = 20;

  private final CamundaClient camundaClient;

  public CamundaClientSecretResolver(CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  @Override
  public Map<String, String> resolve(Collection<String> references) {
    var distinctReferences = List.copyOf(new LinkedHashSet<>(references));
    Map<String, String> resolved = new HashMap<>();
    for (List<String> chunk : Lists.partition(distinctReferences, MAX_REFERENCES_PER_CALL)) {
      resolveChunk(chunk, resolved);
    }
    return resolved;
  }

  /**
   * A transport failure, or a 404 from a cluster that predates secret resolution, is handled the
   * same way as a per-reference {@code errors} entry: log and move on with whatever else resolved.
   * That degrades to the existing "secret not available" outcome downstream rather than crashing.
   */
  private void resolveChunk(List<String> chunk, Map<String, String> resolved) {
    try {
      var response = camundaClient.newResolveSecretsCommand().references(chunk).send().join();
      response
          .getResolved()
          .forEach(secret -> resolved.put(secret.getReference(), secret.getValue()));
      response
          .getErrors()
          .forEach(
              error ->
                  LOG.warn(
                      "Secret reference '{}' could not be resolved: {} ({})",
                      error.getReference(),
                      error.getCode(),
                      error.getMessage()));
    } catch (RuntimeException e) {
      // Log only the exception type, never getMessage(): an HTTP client exception's message can
      // include response body content, and this class exists specifically to handle secret
      // material — same policy as ConfigurationValidationService's exception logging.
      LOG.warn(
          "Failed to resolve {} secret reference(s) via POST /v2/secrets/resolve ({})",
          chunk.size(),
          e.getClass().getName());
    }
  }
}
