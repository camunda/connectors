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

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A reference to a cluster variable, {@code camunda.vars.<scope>.<name>}, as it appears in a
 * connector's raw properties.
 *
 * <p>This exists only to work out which cluster variables a set of properties names, so the
 * allow-list of {@link SecretReferenceAllowList} can be widened by the secret references those
 * variables declare. The runtime never substitutes a cluster variable's value: that stays with
 * expression evaluation on the cluster, which owns the scope rules and any trailing field path. See
 * ADR-0007 §12.
 */
public record ClusterVariableReference(Scope scope, String name) {

  /**
   * The scopes the engine recognises for this syntax, mirroring its own {@code
   * ClusterVariableReference}. {@code camunda.vars.processInstance.*} and anything else is not a
   * cluster variable and is deliberately not matched.
   */
  public enum Scope {
    ENV,
    TENANT,
    CLUSTER
  }

  /**
   * Matches the {@code camunda.vars.<scope>.<name>} prefix of a reference. A trailing field path
   * ({@code camunda.vars.env.myVar.a.b}) is part of the reference for the engine, but which
   * variable to read is decided by scope and name alone, so this stops at the name and lets the
   * rest fall where it may.
   *
   * <p>Like {@link SecretReferenceUtil#PATTERN} this works on raw text rather than a parsed FEEL
   * expression, so it can also match inside a string literal. That is safe here in a way it would
   * not be elsewhere: a match only ever widens an allow-list, and a spurious one widens it by
   * whatever a {@code SECRET_REFERENCE} cluster variable of that name legitimately declares.
   */
  private static final Pattern PATTERN =
      Pattern.compile("camunda\\.vars\\.(?<scope>env|tenant|cluster)\\.(?<name>[\\p{Alnum}_]+)");

  public ClusterVariableReference {
    Objects.requireNonNull(scope, "scope");
    Objects.requireNonNull(name, "name");
  }

  /**
   * Every distinct reference in {@code input}, in the order first seen so callers and their tests
   * get a stable result. Returns nothing for {@code null} input, which is what keeps a request that
   * names no cluster variable from costing anything.
   */
  public static Set<ClusterVariableReference> findAll(String input) {
    if (input == null) {
      return Set.of();
    }
    Set<ClusterVariableReference> references = new LinkedHashSet<>();
    var matcher = PATTERN.matcher(input);
    while (matcher.find()) {
      references.add(
          new ClusterVariableReference(
              Scope.valueOf(matcher.group("scope").toUpperCase()), matcher.group("name")));
    }
    return references;
  }
}
