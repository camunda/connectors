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
   * References that could not be resolved are absent from the result, whether the cluster declined
   * them individually or the request failed outright.
   */
  public Map<String, String> resolve(Collection<String> references) {
    return resolve(references, false);
  }

  /**
   * Resolves as {@link #resolve} does, except that a request which fails outright propagates
   * instead of being reported as a set of references the cluster does not hold.
   *
   * <p>The two are not the same thing, and a caller that turns an unresolved reference into a
   * permanent failure has to tell them apart: a name the stores do not hold is a modelling error
   * that will never resolve, while a cluster that could not be reached is transient and the same
   * lookup will succeed on the next attempt. Reporting the latter as a miss fails the job for good
   * over a network blip.
   *
   * <p>A reference the cluster did answer about but declined — a misspelled name, a missing grant —
   * is still simply absent, because that answer is definitive.
   */
  public Map<String, String> resolveOrFail(Collection<String> references) {
    return resolve(references, true);
  }

  private Map<String, String> resolve(Collection<String> references, boolean propagateFailure) {
    Map<String, String> resolved = new HashMap<>();
    List<String> batch = List.copyOf(references);
    for (int from = 0; from < batch.size(); from += MAX_REFERENCES_PER_REQUEST) {
      int to = Math.min(from + MAX_REFERENCES_PER_REQUEST, batch.size());
      resolveBatch(batch.subList(from, to), resolved, propagateFailure);
    }
    return resolved;
  }

  private void resolveBatch(
      List<String> references, Map<String, String> into, boolean propagateFailure) {
    final ResolveSecretsResponse response;
    try {
      response = camundaClient.newResolveSecretsCommand().references(references).send().join();
    } catch (Exception e) {
      // Never log the exception itself, and never carry it as a cause: a client error message can
      // echo the response body. The class name is what a reader needs and all that is safe to keep.
      LOG.warn(
          "Failed to resolve {} secret reference(s) from the cluster ({})",
          references.size(),
          e.getClass().getName());
      if (propagateFailure) {
        throw new SecretResolutionFailedException(references.size(), e.getClass().getName());
      }
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

  /**
   * A request to the cluster's secret stores failed outright, so nothing is known about the
   * references it carried.
   *
   * <p>Deliberately not a {@code ConnectorInputException}, and deliberately without a cause: the
   * runtime treats that type — directly or as a cause — as a permanent input error and fails the
   * job without retrying, which is the opposite of what an unreachable cluster warrants.
   */
  public static class SecretResolutionFailedException extends RuntimeException
      implements SecretFailureDiagnostic {

    public SecretResolutionFailedException(int referenceCount, String causeType) {
      super(
          "Could not read "
              + referenceCount
              + " secret reference(s) from the cluster's secret stores ("
              + causeType
              + "). This may be transient; the values are not known to be missing.");
    }

    /** Built here from a count and a class name, so it holds nothing the cluster returned. */
    @Override
    public String publishableMessage() {
      return getMessage();
    }
  }
}
