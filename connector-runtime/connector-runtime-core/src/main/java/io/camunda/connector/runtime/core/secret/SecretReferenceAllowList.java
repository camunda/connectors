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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The {@code camunda.secrets.<name>} references the runtime is willing to resolve for one set of
 * raw properties. Anything not on the list is left alone, however much it looks like a reference.
 *
 * <p>A reference earns its place one of two ways (ADR-0007 §12):
 *
 * <ul>
 *   <li>the model writes it in a raw property, anywhere in the value;
 *   <li>a {@code SECRET_REFERENCE} cluster variable that a raw property names declares it.
 * </ul>
 *
 * <p>Being on the list is not the same as being resolvable, and the first source is deliberately
 * wider than what any one pass can substitute. A reference is permitted because the model declared
 * it — authored model text is not a way in, since whoever can deploy a model can already name any
 * secret. Where it can actually be substituted is a separate question: {@link
 * #resolvableInProperties()} is the subset the pass over the raw properties handles (ADR-0007 §11),
 * and a reference mixed into a larger expression is permitted here but only resolves once the
 * cluster returns it from evaluation (ADR-0007 §13).
 *
 * <p>The list only ever permits. Failing to find a reference that should have been on it means a
 * secret does not resolve and the connector fails where the reference was used — never that a wrong
 * value is substituted. That is what makes it safe to build the second half from a cluster read
 * that may be stale, incomplete, or unavailable.
 */
public final class SecretReferenceAllowList {

  private static final SecretReferenceAllowList EMPTY =
      new SecretReferenceAllowList(Set.of(), Set.of(), Set.of());

  private final Set<String> resolvableInProperties;
  private final Set<String> declaredInProperties;
  private final Set<String> declaredByClusterVariables;

  private SecretReferenceAllowList(
      Set<String> resolvableInProperties,
      Set<String> declaredInProperties,
      Set<String> declaredByClusterVariables) {
    this.resolvableInProperties = Set.copyOf(resolvableInProperties);
    this.declaredInProperties = Set.copyOf(declaredInProperties);
    this.declaredByClusterVariables = Set.copyOf(declaredByClusterVariables);
  }

  /** Permits nothing: for callers with no properties to read, and as the fail-closed default. */
  public static SecretReferenceAllowList empty() {
    return EMPTY;
  }

  /**
   * Builds the list for {@code rawPropertyValues} — the property values exactly as the model
   * carries them, before any evaluation.
   *
   * <p>{@code reader} is only consulted when a property actually names a cluster variable, so
   * properties using neither secrets nor cluster variables cost nothing at all.
   */
  public static SecretReferenceAllowList from(
      Collection<String> rawPropertyValues, ClusterVariableSecretReader reader, String tenantId) {
    if (rawPropertyValues == null || rawPropertyValues.isEmpty()) {
      return EMPTY;
    }
    Set<String> resolvable = new LinkedHashSet<>();
    Set<String> declared = new LinkedHashSet<>();
    Set<ClusterVariableReference> clusterVariables = new LinkedHashSet<>();
    for (var value : rawPropertyValues) {
      SecretReferenceUtil.wholeValueReference(value).ifPresent(resolvable::add);
      declared.addAll(SecretReferenceUtil.findReferences(value));
      clusterVariables.addAll(ClusterVariableReference.findAll(value));
    }
    if (clusterVariables.isEmpty()) {
      return declared.isEmpty()
          ? EMPTY
          : new SecretReferenceAllowList(resolvable, declared, Set.of());
    }
    return new SecretReferenceAllowList(
        resolvable, declared, reader.declaredReferences(clusterVariables, tenantId));
  }

  /**
   * @param reference a whole {@code "camunda.secrets.<name>"} string
   */
  public boolean allows(String reference) {
    return declaredInProperties.contains(reference)
        || declaredByClusterVariables.contains(reference);
  }

  /** Whether nothing at all may be resolved, so callers can skip the resolve call entirely. */
  public boolean isEmpty() {
    return declaredInProperties.isEmpty() && declaredByClusterVariables.isEmpty();
  }

  /**
   * The subset the pass over the raw properties substitutes: a reference that is a property's whole
   * value, written as an expression (ADR-0007 §11). Always a subset of what {@link #allows(String)}
   * permits.
   */
  public Set<String> resolvableInProperties() {
    return resolvableInProperties;
  }

  /** Every reference on the list, whichever way it got there. */
  public Set<String> all() {
    Set<String> all = new LinkedHashSet<>(declaredInProperties);
    all.addAll(declaredByClusterVariables);
    return all;
  }
}
