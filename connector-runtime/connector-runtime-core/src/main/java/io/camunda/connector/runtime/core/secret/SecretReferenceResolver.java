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

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ResolveSecretsResponse;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads {@code camunda.secrets.<name>} values from the orchestration cluster's secret stores.
 *
 * <p>The endpoint answers successfully even when individual references fail, reporting a cause per
 * reference. A reference that fails is simply absent from the returned map, and the caller leaves
 * its placeholder in place; the cause is logged, because a missing grant would otherwise be
 * indistinguishable from a misspelled name.
 */
public class SecretReferenceResolver {

  /** The endpoint rejects a request carrying more references than this. */
  static final int MAX_REFERENCES_PER_REQUEST = 20;

  private static final Logger LOG = LoggerFactory.getLogger(SecretReferenceResolver.class);

  private final CamundaClient camundaClient;

  public SecretReferenceResolver(CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  /**
   * Resolves the given whole references (e.g. {@code camunda.secrets.TOKEN}), keyed by reference.
   * References that could not be resolved are absent from the result.
   */
  public Map<String, String> resolve(Collection<String> references) {
    Map<String, String> resolved = new HashMap<>();
    List<String> batch = List.copyOf(references);
    for (int from = 0; from < batch.size(); from += MAX_REFERENCES_PER_REQUEST) {
      int to = Math.min(from + MAX_REFERENCES_PER_REQUEST, batch.size());
      resolveBatch(batch.subList(from, to), resolved);
    }
    return resolved;
  }

  private void resolveBatch(List<String> references, Map<String, String> into) {
    final ResolveSecretsResponse response;
    try {
      response = camundaClient.newResolveSecretsCommand().references(references).send().join();
    } catch (Exception e) {
      // Never log the exception itself: a client error message can echo the response body.
      LOG.warn(
          "Failed to resolve {} secret reference(s) from the cluster ({}); their placeholders are"
              + " left unresolved",
          references.size(),
          e.getClass().getName());
      return;
    }
    response.getResolved().forEach(secret -> into.put(secret.getReference(), secret.getValue()));
    response
        .getErrors()
        .forEach(
            error ->
                LOG.warn(
                    "Secret reference '{}' could not be resolved: {} ({})",
                    error.getReference(),
                    error.getCode(),
                    error.getMessage()));
  }
}
