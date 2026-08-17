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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Substitutes the {@code camunda.secrets.<name>} references a connector's raw properties write as a
 * whole property value: {@code =camunda.secrets.NAME} becomes the secret (ADR-0007 §11).
 *
 * <p>Works on the property structure rather than on its serialized JSON, which is what makes "whole
 * value" mean what it says. A value is either entirely a reference or it is left alone, so no
 * property text is scanned for reference-shaped substrings and a secret value never has to be
 * escaped back into a JSON document — it is placed as a value and serialized later like any other.
 *
 * <p>What this pass deliberately does not handle: a reference mixed into a larger expression. That
 * is permitted by the allow-list but only becomes substitutable once the cluster returns the
 * reference from expression evaluation (ADR-0007 §13), which is a later pass, not this one.
 */
public class SecretReferencePropertyResolver {

  private static final Logger LOG = LoggerFactory.getLogger(SecretReferencePropertyResolver.class);

  private final SecretReferenceResolver referenceResolver;
  private final SecretFilter secretFilter;

  public SecretReferencePropertyResolver(
      SecretReferenceResolver referenceResolver, SecretFilter secretFilter) {
    this.referenceResolver = Objects.requireNonNull(referenceResolver, "referenceResolver");
    this.secretFilter = Objects.requireNonNull(secretFilter, "secretFilter");
  }

  /**
   * Returns {@code properties} with every allowed whole-value reference replaced by its secret.
   *
   * <p>All the references in one call are resolved together, and a set of properties holding none
   * is returned untouched without asking the cluster anything.
   *
   * @param allowList decides what may be resolved; a reference it does not permit is left in place
   *     exactly as written, the same outcome as a secret the filter refuses
   * @throws ConnectorInputException if a permitted reference has no value, matching how a missing
   *     secret already fails
   */
  public Map<String, Object> resolve(
      Map<String, Object> properties, SecretReferenceAllowList allowList) {
    if (properties == null
        || properties.isEmpty()
        || allowList.resolvableInProperties().isEmpty()) {
      return properties;
    }
    var requested = permitted(allowList);
    if (requested.isEmpty()) {
      return properties;
    }
    var resolved = referenceResolver.resolve(requested);
    return replaceIn(properties, allowList, resolved);
  }

  /**
   * The references to ask for: those the allow-list says this pass may substitute, minus any the
   * {@link SecretFilter} refuses. A refused name is left in the text rather than failing, which is
   * the distinction ADR-0007 §7 keeps — the filter is keyed by the bare name, never by the whole
   * reference.
   */
  private List<String> permitted(SecretReferenceAllowList allowList) {
    List<String> requested = new ArrayList<>();
    for (var reference : allowList.resolvableInProperties()) {
      if (secretFilter.isAllowed(SecretReferenceUtil.bareName(reference))) {
        requested.add(reference);
      } else {
        LOG.debug("Secret '{}' not in allow-list — reference left unreplaced", reference);
      }
    }
    return requested;
  }

  private Object replaceIn(
      Object node, SecretReferenceAllowList allowList, Map<String, String> resolved) {
    return switch (node) {
      case String value -> replaceValue(value, allowList, resolved);
      case Map<?, ?> map -> {
        Map<Object, Object> copy = new LinkedHashMap<>();
        map.forEach((key, value) -> copy.put(key, replaceIn(value, allowList, resolved)));
        yield copy;
      }
      case List<?> list -> list.stream().map(item -> replaceIn(item, allowList, resolved)).toList();
      case null, default -> node;
    };
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> replaceIn(
      Map<String, Object> properties,
      SecretReferenceAllowList allowList,
      Map<String, String> resolved) {
    return (Map<String, Object>) replaceIn((Object) properties, allowList, resolved);
  }

  /**
   * A value that is entirely a reference becomes its secret. Anything else is returned as it is — a
   * reference embedded in a longer value, or one mixed into a larger expression, is not this pass's
   * business.
   */
  private Object replaceValue(
      String value, SecretReferenceAllowList allowList, Map<String, String> resolved) {
    var reference = SecretReferenceUtil.wholeValueReference(value).orElse(null);
    if (reference == null || !allowList.resolvableInProperties().contains(reference)) {
      return value;
    }
    if (resolved.containsKey(reference)) {
      return resolved.get(reference);
    }
    if (!secretFilter.isAllowed(SecretReferenceUtil.bareName(reference))) {
      return value;
    }
    throw new ConnectorInputException(
        String.format(
            "Secret with name '%s' is not available", SecretReferenceUtil.bareName(reference)));
  }
}
