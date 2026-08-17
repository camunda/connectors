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
import java.util.Set;

/**
 * Finds the {@code camunda.secrets.<name>} references that {@code SECRET_REFERENCE} cluster
 * variables declare, for the variables a connector's properties name.
 *
 * <p>A cluster variable of kind {@code JSON} declares nothing, however much its contents may look
 * like a reference — the same rule the engine applies when it resolves these for a job. That rule
 * is the whole point of this type: without it, any text shaped like a reference would resolve. See
 * ADR-0007 §9 to §12.
 */
public interface ClusterVariableSecretReader {

  /**
   * @param references the cluster variables named in the raw properties, from {@link
   *     ClusterVariableReference#findAll(String)}
   * @param tenantId the connector's tenant, which decides what {@code env} and {@code tenant} scope
   *     resolve to
   * @return the whole {@code "camunda.secrets.<name>"} references those variables declare, empty if
   *     they declare none or could not be read. Only ever used to widen an allow-list, so coming
   *     back empty makes a secret fail to resolve rather than letting a wrong one through.
   */
  Set<String> declaredReferences(Collection<ClusterVariableReference> references, String tenantId);

  /** A reader with no cluster to ask: declares nothing, for no cost. */
  static ClusterVariableSecretReader noop() {
    return (references, tenantId) -> Set.of();
  }
}
