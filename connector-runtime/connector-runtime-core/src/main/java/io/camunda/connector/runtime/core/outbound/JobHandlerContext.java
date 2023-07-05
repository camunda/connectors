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
package io.camunda.connector.runtime.core.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.impl.context.AbstractConnectorContext;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import java.util.Map;
import java.util.Objects;

/**
 * Implementation of {@link io.camunda.connector.api.outbound.OutboundConnectorContext} passed on to
 * a {@link io.camunda.connector.api.outbound.OutboundConnectorFunction} when called from the {@link
 * ConnectorJobHandler}.
 */
public class JobHandlerContext extends AbstractConnectorContext
    implements OutboundConnectorContext {

  private final ActivatedJob job;

  private final ObjectMapper objectMapper;

  private String jsonWithSecrets = null;

  public JobHandlerContext(
      final ActivatedJob job,
      final SecretProvider secretProvider,
      final ValidationProvider validationProvider,
      final ObjectMapper objectMapper) {
    super(secretProvider, validationProvider);
    this.job = job;
    this.objectMapper = objectMapper;
  }

  @Override
  public Map<String, String> getCustomerHeaders() {
    return job.getCustomHeaders();
  }

  @Override
  public <T> T bindVariables(Class<T> cls) {
    var mappedObject = mapJson(getJsonReplacedWithSecrets(), cls);
    getValidationProvider().validate(mappedObject);
    return mappedObject;
  }

  private String getJsonReplacedWithSecrets() {
    if (jsonWithSecrets == null) {
      try {
        jsonWithSecrets = getSecretHandler().replaceSecrets(job.getVariables());
      } catch (Exception e) {
        throw new ConnectorException("SECRET_MAPPING", "Error during secret mapping.");
      }
    }
    return jsonWithSecrets;
  }

  private <T> T mapJson(String json, Class<T> cls) {
    try {
      return objectMapper.readValue(getJsonReplacedWithSecrets(), cls);
    } catch (Exception e) {
      throw new ConnectorException("JSON_MAPPING", "Error during json mapping.");
    }
  }

  @Override
  public String getVariables() {
    return getJsonReplacedWithSecrets();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    JobHandlerContext that = (JobHandlerContext) o;
    return Objects.equals(job, that.job);
  }

  @Override
  public int hashCode() {
    return Objects.hash(job);
  }
}
