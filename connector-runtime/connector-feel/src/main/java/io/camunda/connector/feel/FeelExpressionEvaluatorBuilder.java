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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;

/**
 * Step builder for {@link FeelExpressionEvaluator} instances. The entry points return distinct
 * sub-builders so that backend-specific options (e.g. {@code scopeKey}, {@code tenantId}) are only
 * reachable for the backend that actually supports them.
 *
 * <p>Pick {@link #local()} for embedded FEEL evaluation, or {@link #camundaClient(CamundaClient)}
 * for cluster-based evaluation (allowing access to cluster variables like {@code
 * camunda.vars.env.*}).
 *
 * <pre>{@code
 * FeelExpressionEvaluator local = FeelExpressionEvaluatorBuilder.local().build();
 *
 * FeelExpressionEvaluator cluster = FeelExpressionEvaluatorBuilder.camundaClient(client)
 *     .tenantId("acme")
 *     .scopeKey(elementInstanceKey)
 *     .objectMapper(objectMapper)
 *     .build();
 * }</pre>
 */
public final class FeelExpressionEvaluatorBuilder {

  private FeelExpressionEvaluatorBuilder() {}

  /** Start building a {@link LocalFeelExpressionEvaluator}. */
  public static LocalStep local() {
    return new LocalStep();
  }

  /** Start building a {@link CamundaClientFeelExpressionEvaluator}. */
  public static CamundaClientStep camundaClient(CamundaClient camundaClient) {
    if (camundaClient == null) {
      throw new IllegalArgumentException("camundaClient must not be null");
    }
    return new CamundaClientStep(camundaClient);
  }

  /** Step builder for the embedded FEEL engine evaluator. */
  public static final class LocalStep {
    private LocalStep() {}

    public FeelExpressionEvaluator build() {
      return new LocalFeelExpressionEvaluator();
    }
  }

  /** Step builder for the cluster-based evaluator. */
  public static final class CamundaClientStep {
    private final CamundaClient camundaClient;
    private String tenantId;
    private Long scopeKey;
    private ObjectMapper objectMapper;

    private CamundaClientStep(CamundaClient camundaClient) {
      this.camundaClient = camundaClient;
    }

    public CamundaClientStep tenantId(String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public CamundaClientStep scopeKey(Long scopeKey) {
      this.scopeKey = scopeKey;
      return this;
    }

    public CamundaClientStep objectMapper(ObjectMapper objectMapper) {
      this.objectMapper = objectMapper;
      return this;
    }

    public FeelExpressionEvaluator build() {
      ObjectMapper mapper =
          objectMapper != null ? objectMapper : ConnectorsObjectMapperSupplier.getCopy();
      return new CamundaClientFeelExpressionEvaluator(camundaClient, tenantId, scopeKey, mapper);
    }
  }
}
