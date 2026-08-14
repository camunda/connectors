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
import java.util.Map;

/**
 * Resolves {@code camunda.secrets.<name>} references against the orchestration cluster's secret
 * stores, via {@code POST /v2/secrets/resolve}. Deliberately separate from {@link
 * io.camunda.connector.api.secret.SecretProvider}: the two forms of secret are resolved from
 * different places and must not fall back onto one another (see ADR-0007). Unlike {@code
 * SecretProvider}, this takes no {@code SecretContext}: an instance is already scoped to one
 * physical tenant's client (see {@link CamundaClientSecretResolver}), and the cluster works out the
 * rest from the caller's own token.
 */
public interface SecretReferenceResolver {

  /**
   * @param references whole {@code "camunda.secrets.<name>"} strings to resolve
   * @return a map from each resolved reference to its value; a reference absent from the map has no
   *     value, whether because it wasn't found, wasn't permitted, or the resolver never called out
   *     (see {@link #noop()})
   */
  Map<String, String> resolve(Collection<String> references);

  /** A resolver with no cluster to call: always returns no values, for no cost. */
  static SecretReferenceResolver noop() {
    return references -> Map.of();
  }
}
