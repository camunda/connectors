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
import io.camunda.client.api.search.enums.ClusterVariableKind;
import io.camunda.client.api.search.enums.ClusterVariableScope;
import io.camunda.client.api.search.response.ClusterVariable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads {@code SECRET_REFERENCE} cluster variables through one {@link CamundaClient}, in a single
 * search, and reports the {@code camunda.secrets.<name>} references they declare.
 *
 * <p>One search covers every variable named in the request: the filter takes the whole list of
 * names and the kind, so the cluster returns only the variables allowed to declare secrets. Nothing
 * is asked for when the request names no cluster variable.
 */
public class CamundaClientClusterVariableSecretReader implements ClusterVariableSecretReader {

  private static final Logger LOG =
      LoggerFactory.getLogger(CamundaClientClusterVariableSecretReader.class);

  /**
   * A name can legitimately match two variables — one global, one for this tenant — and the search
   * is not filtered by tenant, so other tenants' variables of the same name can come back too. The
   * limit leaves room for that rather than sizing exactly to the name count, and anything beyond it
   * is reported rather than passed over in silence.
   */
  private static final int HEADROOM_PER_NAME = 4;

  private static final int MIN_PAGE_LIMIT = 20;

  private final CamundaClient camundaClient;

  public CamundaClientClusterVariableSecretReader(CamundaClient camundaClient) {
    this.camundaClient = Objects.requireNonNull(camundaClient, "camundaClient must not be null");
  }

  @Override
  public Set<String> declaredReferences(
      Collection<ClusterVariableReference> references, String tenantId) {
    if (references == null || references.isEmpty()) {
      return Set.of();
    }
    var names = references.stream().map(ClusterVariableReference::name).distinct().toList();
    var variables = search(names);
    if (variables.isEmpty()) {
      return Set.of();
    }
    Set<String> declared = new LinkedHashSet<>();
    for (var reference : references) {
      resolve(reference, variables, tenantId)
          .ifPresent(variable -> declared.addAll(SecretReferenceUtil.findReferences(variable)));
    }
    return declared;
  }

  /**
   * A transport failure, a cluster without the secondary storage this search needs, or a rejected
   * request all mean the same thing here: nothing is declared, so nothing extra is allowed and the
   * secrets involved fail to resolve rather than resolving from an unverified source.
   */
  private List<ClusterVariable> search(List<String> names) {
    try {
      var response =
          camundaClient
              .newClusterVariableSearchRequest()
              .filter(
                  filter ->
                      filter
                          .name(name -> name.in(names))
                          .kind(ClusterVariableKind.SECRET_REFERENCE))
              // Without this the value can come back cut short, and a reference past the cut would
              // silently not be allowed.
              .withFullValues()
              .page(page -> page.limit(pageLimit(names.size())))
              .send()
              .join();
      if (Boolean.TRUE.equals(response.page().hasMoreTotalItems())) {
        LOG.warn(
            "More than {} SECRET_REFERENCE cluster variables matched {} name(s); secrets declared by"
                + " the ones beyond that will not resolve",
            pageLimit(names.size()),
            names.size());
      }
      return response.items();
    } catch (RuntimeException e) {
      // Log only the exception type, never getMessage(): this class handles values that hold secret
      // references, and an HTTP client exception's message can carry response body content — same
      // policy as CamundaClientSecretResolver.
      LOG.warn(
          "Failed to read {} SECRET_REFERENCE cluster variable name(s) ({}); secrets they declare"
              + " will not resolve",
          names.size(),
          e.getClass().getName());
      return List.of();
    }
  }

  private static int pageLimit(int nameCount) {
    return Math.max(MIN_PAGE_LIMIT, nameCount * HEADROOM_PER_NAME);
  }

  /**
   * Picks the variable a reference names, following the engine's own scope rules (see its {@code
   * ClusterVariableJobSecretResolver}): {@code tenant} resolves at tenant scope only, {@code
   * cluster} at global scope only, and {@code env} at tenant scope falling back to global. A
   * tenant-scoped variable with an empty value does not satisfy {@code env}, matching the engine.
   */
  private static Optional<String> resolve(
      ClusterVariableReference reference, List<ClusterVariable> variables, String tenantId) {
    return switch (reference.scope()) {
      case TENANT -> tenantScoped(reference.name(), variables, tenantId);
      case CLUSTER -> globallyScoped(reference.name(), variables);
      case ENV ->
          tenantScoped(reference.name(), variables, tenantId)
              .filter(value -> !value.isEmpty())
              .or(() -> globallyScoped(reference.name(), variables));
    };
  }

  private static Optional<String> tenantScoped(
      String name, List<ClusterVariable> variables, String tenantId) {
    return variables.stream()
        .filter(variable -> matches(variable, name, ClusterVariableScope.TENANT))
        .filter(variable -> Objects.equals(variable.getTenantId(), tenantId))
        .findFirst()
        .map(ClusterVariable::getValue);
  }

  private static Optional<String> globallyScoped(String name, List<ClusterVariable> variables) {
    return variables.stream()
        .filter(variable -> matches(variable, name, ClusterVariableScope.GLOBAL))
        .findFirst()
        .map(ClusterVariable::getValue);
  }

  /**
   * The kind is re-checked here even though the search already filtered on it. The client's enum
   * carries an {@code UNKNOWN_ENUM_VALUE} for a kind it does not recognise, so a newer cluster
   * introducing a third kind must not have it read as {@code SECRET_REFERENCE} — this fails closed
   * on anything that is not exactly that kind.
   */
  private static boolean matches(
      ClusterVariable variable, String name, ClusterVariableScope expectedScope) {
    return Objects.equals(variable.getName(), name)
        && variable.getScope() == expectedScope
        && variable.getKind() == ClusterVariableKind.SECRET_REFERENCE
        && variable.getValue() != null;
  }
}
