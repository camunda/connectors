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
package io.camunda.connector.feel;

import io.camunda.client.api.response.SecretReference;
import java.util.List;

/**
 * Post-processes the raw result of a cluster expression evaluation, before the result is converted
 * to the caller's target type.
 *
 * <p>A {@code camunda.secrets.<name>} reference survives evaluation as placeholder text, and the
 * cluster reports which references the evaluation actually used. Both are only available at the
 * point where the response is received, which is why this hook exists: it lets the connector
 * runtime substitute secret values into the result without any caller having to know that the
 * expression touched a secret.
 *
 * <p>Substituting after evaluation rather than before it is deliberate. The engine records an
 * evaluation request and its result verbatim, so splicing a secret value into the expression source
 * would persist that value in the cluster.
 */
@FunctionalInterface
public interface EvaluationResultProcessor {

  /** Returns the result unchanged. */
  EvaluationResultProcessor NOOP = (result, referencedSecrets) -> result;

  /**
   * @param result the raw evaluation result: a JSON-shaped graph of {@code Map}, {@code List},
   *     {@code String}, numbers, booleans and {@code null}
   * @param referencedSecrets the secret references this evaluation used, as reported by the
   *     cluster, or {@code null} when the cluster did not report them
   * @return the result to convert and hand to the caller
   */
  Object process(Object result, List<SecretReference> referencedSecrets);
}
