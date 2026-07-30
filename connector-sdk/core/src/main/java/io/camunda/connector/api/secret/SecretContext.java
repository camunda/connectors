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
package io.camunda.connector.api.secret;

import org.jspecify.annotations.Nullable;

/**
 * Scope in which a secret is resolved.
 *
 * @param tenantId the logical (multi-tenancy) tenant the process belongs to
 * @param processDefinitionId the BPMN process definition ID the secret is resolved for
 * @param physicalTenantId identifies the orchestration cluster (engine) the process runs on, so
 *     that secrets can be resolved per engine in multi-engine deployments. {@code null} when the
 *     runtime could not determine it, e.g. against a cluster that predates multi-engine support.
 */
public record SecretContext(
    String tenantId, String processDefinitionId, @Nullable String physicalTenantId) {

  public SecretContext {
    // clients report an unset physical tenant as an empty string (protobuf/REST default);
    // normalize it so providers only have to check for null
    if (physicalTenantId != null && physicalTenantId.isBlank()) {
      physicalTenantId = null;
    }
  }

  /**
   * Retains the pre-multi-engine constructor signature so code compiled against the previous record
   * shape keeps working. Leaves {@link #physicalTenantId()} unset.
   */
  public SecretContext(String tenantId, String processDefinitionId) {
    this(tenantId, processDefinitionId, null);
  }
}
