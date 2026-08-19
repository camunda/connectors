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

import io.camunda.client.api.response.SecretReference;
import io.camunda.connector.feel.EvaluationResultProcessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Substitutes {@code camunda.secrets.<name>} values into the result of a cluster expression
 * evaluation.
 *
 * <p>This is the single point in the runtime where a secret value replaces its reference. Two
 * things reach it: a reference the model wrote into a property, and a reference that only exists
 * once the cluster has answered, because a {@code SECRET_REFERENCE} cluster variable holds
 * reference text in place of a value.
 *
 * <p>What may be substituted is decided by the cluster, not here. The evaluation response reports
 * the references that evaluation actually used, derived from the parsed expression and from the
 * references stored on the cluster variables it read. Only those are resolved, and only within that
 * one evaluation's result. Reference-shaped text that arrived as data — a webhook payload, a plain
 * {@code JSON} cluster variable — is reported by nothing and therefore stays literal.
 *
 * <p>Scoping the allow-list to a single evaluation is what makes that hold. A list pooled across a
 * whole property binding would let attacker-supplied text in one property be substituted because a
 * different property legitimately named a secret.
 */
public class SecretResolvingResultProcessor implements EvaluationResultProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(SecretResolvingResultProcessor.class);

  private final SecretReferenceResolver resolver;
  private final AtomicBoolean unreportedSecretsWarned = new AtomicBoolean();

  public SecretResolvingResultProcessor(SecretReferenceResolver resolver) {
    this.resolver = resolver;
  }

  @Override
  public Object process(Object result, List<SecretReference> referencedSecrets) {
    if (result == null) {
      return null;
    }
    if (referencedSecrets == null) {
      warnOnceAboutMissingReport();
      return result;
    }
    Set<String> allowed = allowList(referencedSecrets);
    if (allowed.isEmpty()) {
      return result;
    }
    Set<String> present = new LinkedHashSet<>();
    collectReferences(result, allowed, present);
    if (present.isEmpty()) {
      return result;
    }
    Map<String, String> values = resolver.resolve(present);
    return values.isEmpty() ? result : substitute(result, values);
  }

  /**
   * The references the cluster reported, as whole reference strings. The store id a report carries
   * is informational: a reference never names a store, so two reports differing only in store id
   * are one reference to resolve.
   */
  private static Set<String> allowList(List<SecretReference> referencedSecrets) {
    Set<String> allowed = new LinkedHashSet<>();
    for (SecretReference reference : referencedSecrets) {
      if (reference != null && reference.getSecretName() != null) {
        allowed.add(SecretReferenceUtil.reference(reference.getSecretName()));
      }
    }
    return allowed;
  }

  private static void collectReferences(Object node, Set<String> allowed, Set<String> into) {
    switch (node) {
      case String text -> {
        for (String reference : SecretReferenceUtil.findReferences(text)) {
          if (allowed.contains(reference)) {
            into.add(reference);
          } else {
            LOG.debug("Reference-shaped text was not reported by the cluster; leaving it as it is");
          }
        }
      }
      case Map<?, ?> map -> map.values().forEach(value -> collectReferences(value, allowed, into));
      case List<?> list -> list.forEach(element -> collectReferences(element, allowed, into));
      default -> {}
    }
  }

  private static Object substitute(Object node, Map<String, String> values) {
    return switch (node) {
      case String text -> SecretReferenceUtil.replaceReferences(text, values);
      case Map<?, ?> map -> {
        Map<Object, Object> substituted = new LinkedHashMap<>();
        map.forEach((key, value) -> substituted.put(key, substitute(value, values)));
        yield substituted;
      }
      case List<?> list -> {
        List<Object> substituted = new ArrayList<>(list.size());
        list.forEach(element -> substituted.add(substitute(element, values)));
        yield substituted;
      }
      default -> node;
    };
  }

  private void warnOnceAboutMissingReport() {
    if (unreportedSecretsWarned.compareAndSet(false, true)) {
      LOG.warn(
          "The cluster did not report which secret references an expression used, so no secret"
              + " reference will be resolved. This needs an orchestration cluster that reports"
              + " referenced secrets on expression evaluation.");
    }
  }
}
