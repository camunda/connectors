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

  /**
   * Static because a processor is not: one is built per inbound connector context and per process
   * instance context, so a per-instance latch would warn once per process instance rather than
   * once, which is the opposite of what warning once is for.
   *
   * <p>Being static, it is also consumed once per process, test process included. A test that
   * asserts the warning has to call {@link #resetUnreportedReferenceWarning()} first, or it passes
   * alone and fails in a suite where something else reached the branch before it.
   */
  private static final AtomicBoolean UNREPORTED_REFERENCE_WARNED = new AtomicBoolean();

  private final SecretReferenceResolver resolver;

  public SecretResolvingResultProcessor(SecretReferenceResolver resolver) {
    this.resolver = resolver;
  }

  @Override
  public Object process(Object result, List<SecretReference> referencedSecrets) {
    if (result == null) {
      return null;
    }
    Set<String> found = new LinkedHashSet<>();
    collectReferences(result, found);
    if (found.isEmpty()) {
      return result;
    }
    Set<String> allowed = allowList(referencedSecrets);
    Set<String> resolvable = new LinkedHashSet<>(found);
    resolvable.retainAll(allowed);
    if (resolvable.size() < found.size()) {
      warnOnceAboutUnreportedReference();
    }
    if (resolvable.isEmpty()) {
      return result;
    }
    Map<String, String> values = resolver.resolve(resolvable);
    return values.isEmpty() ? result : substitute(result, values);
  }

  /**
   * The references the cluster reported, as whole reference strings. The store id a report carries
   * is informational: a reference never names a store, so two reports differing only in store id
   * are one reference to resolve.
   */
  private static Set<String> allowList(List<SecretReference> referencedSecrets) {
    Set<String> allowed = new LinkedHashSet<>();
    if (referencedSecrets == null) {
      return allowed;
    }
    for (SecretReference reference : referencedSecrets) {
      if (reference != null && reference.getSecretName() != null) {
        allowed.add(SecretReferenceUtil.reference(reference.getSecretName()));
      }
    }
    return allowed;
  }

  private static void collectReferences(Object node, Set<String> into) {
    switch (node) {
      // A pattern switch throws on a null selector unless null is matched explicitly, and an
      // evaluation result may hold a null anywhere in it.
      case null -> {}
      case String text -> into.addAll(SecretReferenceUtil.findReferences(text));
      case Map<?, ?> map -> map.values().forEach(value -> collectReferences(value, into));
      case List<?> list -> list.forEach(element -> collectReferences(element, into));
      default -> {}
    }
  }

  private static Object substitute(Object node, Map<String, String> values) {
    return switch (node) {
      case null -> null;
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

  /**
   * Warns once for the whole runtime, not per occurrence and not per context, and without repeating
   * the text. Two very different things reach here: text that was never a reference, which is the
   * defence working, and a cluster too old to report referenced secrets, where every reference goes
   * unresolved. Neither is worth a line per evaluation, and the text can be attacker-supplied.
   */
  /** Re-arms the process-wide latch. For tests that assert on the warning; see the field. */
  static void resetUnreportedReferenceWarning() {
    UNREPORTED_REFERENCE_WARNED.set(false);
  }

  private static void warnOnceAboutUnreportedReference() {
    if (UNREPORTED_REFERENCE_WARNED.compareAndSet(false, true)) {
      LOG.warn(
          "An expression result contained camunda.secrets.<name> text that the cluster did not"
              + " report as a referenced secret, so it was left unresolved. That is expected when"
              + " the text is data rather than a reference a model declared. If a secret is not"
              + " resolving, check that the orchestration cluster reports referenced secrets on"
              + " expression evaluation.");
    }
  }
}
