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

package io.camunda.connector.awslambda.model;

import io.camunda.connector.api.ConnectorInput;
import io.camunda.connector.api.SecretStore;
import io.camunda.connector.api.Validator;
import java.util.Objects;

public class FunctionRequestData implements ConnectorInput {

  private String functionName;
  private Object payload;
  private String region;
  private OperationType operationType; // this is not use and not implemented yet

  @Override
  public void validateWith(final Validator validator) {
    validator.require(functionName, "function name");
    validator.require(payload, "payload");
    validator.require(region, "region");
  }

  @Override
  public void replaceSecrets(final SecretStore secretStore) {
    functionName = secretStore.replaceSecret(functionName);
    region = secretStore.replaceSecret(region);
  }

  public String getFunctionName() {
    return functionName;
  }

  public void setFunctionName(final String functionName) {
    this.functionName = functionName;
  }

  public Object getPayload() {
    return payload;
  }

  public void setPayload(final Object payload) {
    this.payload = payload;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(final String region) {
    this.region = region;
  }

  public OperationType getOperationType() {
    return operationType;
  }

  public void setOperationType(final OperationType operationType) {
    this.operationType = operationType;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final FunctionRequestData that = (FunctionRequestData) o;
    return Objects.equals(functionName, that.functionName)
        && Objects.equals(payload, that.payload)
        && Objects.equals(region, that.region)
        && operationType == that.operationType;
  }

  @Override
  public int hashCode() {
    return Objects.hash(functionName, payload, region, operationType);
  }

  @Override
  public String toString() {
    return "FunctionRequestData{functionName=[REDACTED], "
        + "payload=[REDACTED]}, "
        + "region="
        + region
        + "operationType="
        + operationType
        + "}";
  }
}
