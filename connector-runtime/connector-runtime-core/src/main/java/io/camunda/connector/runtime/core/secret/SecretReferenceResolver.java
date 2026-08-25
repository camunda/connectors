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
import io.camunda.client.api.search.enums.SecretErrorCode;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads {@code camunda.secrets.<name>} values from the orchestration cluster's secret stores.
 *
 * <p>The endpoint answers successfully even when individual references fail, reporting a cause per
 * reference. A reference that fails is absent from the returned map, and the caller leaves its
 * placeholder in place; the cause is logged, because a missing grant would otherwise be
 * indistinguishable from a misspelled name.
 *
 * <p>That is the whole story for {@link #resolve}, which never fails. {@link #resolveOrFail} reads
 * the per-reference cause as well, because a caller that turns an unresolved reference into a
 * permanent failure may only do so on an answer that says the reference will never resolve.
 */
public class SecretReferenceResolver {

  /** The endpoint rejects a request carrying more references than this. */
  static final int MAX_REFERENCES_PER_REQUEST = 20;

  /**
   * The answers that settle a reference for good: the stores were read, and this reference will not
   * resolve however often it is asked for.
   *
   * <p>An allow-list rather than a list of the inconclusive codes, so that a code this runtime has
   * never heard of is a failure by construction rather than by having been enumerated here. An
   * {@link EnumSet} rather than {@link Set#of}, whose {@code contains} throws on the {@code null} a
   * response with no code at all would produce.
   */
  private static final Set<SecretErrorCode> DEFINITIVE_ANSWERS =
      EnumSet.of(
          SecretErrorCode.NOT_FOUND,
          SecretErrorCode.ACCESS_DENIED,
          SecretErrorCode.INVALID_REFERENCE);

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
   * <p>A reference the cluster declined with a definitive answer — a misspelled name, a missing
   * grant — is still simply absent, because that answer settles it for good. An answer that does
   * not, {@link #DEFINITIVE_ANSWERS} being the list of the ones that do, is a failure like an
   * unreachable cluster and propagates as one: {@code UNREADABLE} says the stores hold the name and
   * reading its value failed, which is neither permanent nor a reason to report the secret as
   * missing, and a code this runtime does not recognise says nothing at all. The client maps a code
   * it does not know to {@code UNKNOWN_ENUM_VALUE}, and the engine's own enum is only guaranteed to
   * be handled exhaustively by callers that ship from its repository in the same release — which
   * this runtime does not.
   *
   * <p>{@link #resolve} makes no such distinction, deliberately: leaving the placeholder in place
   * is its answer to every unresolved reference (ADR-0007, Decision 7), and it has no caller that
   * could act on the difference.
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
    if (propagateFailure) {
      var inconclusive =
          response.getErrors().stream()
              .map(ResolveSecretsResponse.ResolutionError::getCode)
              .filter(code -> !DEFINITIVE_ANSWERS.contains(code))
              .toList();
      if (!inconclusive.isEmpty()) {
        // a LinkedHashSet, not Set.copyOf: a response carrying no code at all leaves a null here,
        // and that is exactly the answer this branch exists to refuse
        throw new SecretResolutionFailedException(
            inconclusive.size(), new LinkedHashSet<>(inconclusive));
      }
    }
  }

  /**
   * Nothing is known about a reference: either the request carrying it failed outright, or the
   * cluster answered about it without settling it.
   *
   * <p>Deliberately not a {@code ConnectorInputException}, and deliberately without a cause: the
   * runtime treats that type — directly or as a cause — as a permanent input error and fails the
   * job without retrying, which is the opposite of what an unreachable cluster, or a store that
   * could not read a name it holds, warrants.
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

    /**
     * Raised for references the cluster answered about without settling them. The codes are this
     * client's own vocabulary, not text the cluster wrote, so naming them here carries nothing a
     * secret store returned.
     */
    public SecretResolutionFailedException(
        int referenceCount, Set<SecretErrorCode> inconclusiveCodes) {
      super(
          "Could not read "
              + referenceCount
              + " secret reference(s) from the cluster's secret stores; it reported them as "
              + inconclusiveCodes.stream()
                  .map(code -> code == null ? "no code" : code.name())
                  .sorted()
                  .toList()
              + ". That is not an answer that they are missing, and it may be transient.");
    }

    /**
     * Built here from counts, a class name and this client's own enum, so it holds nothing the
     * cluster returned.
     */
    @Override
    public String publishableMessage() {
      return getMessage();
    }
  }
}
