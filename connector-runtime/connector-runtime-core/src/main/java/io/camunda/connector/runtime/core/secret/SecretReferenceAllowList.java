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
 *   <li>it is written in a raw property, as that property's whole value: {@code
 *       =camunda.secrets.NAME};
 *   <li>a {@code SECRET_REFERENCE} cluster variable that a raw property names declares it.
 * </ul>
 *
 * <p>The list only ever permits. Failing to find a reference that should have been on it means a
 * secret does not resolve and the connector fails where the reference was used — never that a wrong
 * value is substituted. That is what makes it safe to build the second half from a cluster read
 * that may be stale, incomplete, or unavailable.
 */
public final class SecretReferenceAllowList {

  private static final SecretReferenceAllowList EMPTY =
      new SecretReferenceAllowList(Set.of(), Set.of());

  private final Set<String> writtenInProperties;
  private final Set<String> declaredByClusterVariables;

  private SecretReferenceAllowList(
      Set<String> writtenInProperties, Set<String> declaredByClusterVariables) {
    this.writtenInProperties = Set.copyOf(writtenInProperties);
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
    Set<String> written = new LinkedHashSet<>();
    Set<ClusterVariableReference> clusterVariables = new LinkedHashSet<>();
    for (var value : rawPropertyValues) {
      SecretReferenceUtil.wholeValueReference(value).ifPresent(written::add);
      clusterVariables.addAll(ClusterVariableReference.findAll(value));
    }
    if (clusterVariables.isEmpty()) {
      return written.isEmpty() ? EMPTY : new SecretReferenceAllowList(written, Set.of());
    }
    return new SecretReferenceAllowList(
        written, reader.declaredReferences(clusterVariables, tenantId));
  }

  /**
   * @param reference a whole {@code "camunda.secrets.<name>"} string
   */
  public boolean allows(String reference) {
    return writtenInProperties.contains(reference)
        || declaredByClusterVariables.contains(reference);
  }

  /** Whether nothing at all may be resolved, so callers can skip the resolve call entirely. */
  public boolean isEmpty() {
    return writtenInProperties.isEmpty() && declaredByClusterVariables.isEmpty();
  }

  /**
   * The references written directly in the properties. These are the ones a pass over the raw
   * properties resolves, before anything is evaluated.
   */
  public Set<String> writtenInProperties() {
    return writtenInProperties;
  }

  /** Every reference on the list, whichever way it got there. */
  public Set<String> all() {
    Set<String> all = new LinkedHashSet<>(writtenInProperties);
    all.addAll(declaredByClusterVariables);
    return all;
  }
}
