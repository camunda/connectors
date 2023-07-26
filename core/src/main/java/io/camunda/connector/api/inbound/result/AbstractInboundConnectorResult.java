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
package io.camunda.connector.api.inbound.result;

import io.camunda.connector.api.inbound.InboundConnectorResult;
import java.util.Objects;
import java.util.Optional;

public abstract class AbstractInboundConnectorResult<T> implements InboundConnectorResult<T> {

  protected final String type;
  protected final String correlationPointId;
  protected final boolean activated;
  protected final T responseData;
  protected final CorrelationErrorData errorData;

  public AbstractInboundConnectorResult(
      String type,
      String correlationPointId,
      boolean activated,
      T responseData,
      CorrelationErrorData errorData) {
    this.type = type;
    this.correlationPointId = correlationPointId;
    this.activated = activated;
    this.responseData = responseData;
    this.errorData = errorData;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public String getCorrelationPointId() {
    return correlationPointId;
  }

  @Override
  public boolean isActivated() {
    return activated;
  }

  @Override
  public Optional<T> getResponseData() {
    return Optional.ofNullable(responseData);
  }

  @Override
  public Optional<CorrelationErrorData> getErrorData() {
    return Optional.ofNullable(errorData);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AbstractInboundConnectorResult<?> that = (AbstractInboundConnectorResult<?>) o;
    return activated == that.activated
        && Objects.equals(type, that.type)
        && Objects.equals(correlationPointId, that.correlationPointId)
        && Objects.equals(responseData, that.responseData)
        && Objects.equals(errorData, that.errorData);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, correlationPointId, activated, responseData, errorData);
  }

  @Override
  public String toString() {
    return "InboundConnectorResult{"
        + "type='"
        + type
        + '\''
        + ", correlationPointId='"
        + correlationPointId
        + '\''
        + ", activated="
        + activated
        + ", responseData="
        + responseData
        + ", errorData="
        + errorData
        + '}';
  }
}
