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
package io.camunda.connector.runtime.inbound.lifecycle;

import java.util.Map;
import java.util.Objects;

public class ActiveInboundConnectorResponse {

  private final String bpmnProcessId;
  private final String elementId;
  private final String type;
  private final Map<String, Object> data;

  public ActiveInboundConnectorResponse(
      final String bpmnProcessId,
      final String elementId,
      final String type,
      final Map<String, Object> data) {

    this.bpmnProcessId = bpmnProcessId;
    this.elementId = elementId;
    this.type = type;
    this.data = data;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public String getElementId() {
    return elementId;
  }

  public String getType() {
    return type;
  }

  public Map<String, Object> getData() {
    return data;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ActiveInboundConnectorResponse that = (ActiveInboundConnectorResponse) o;
    return Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(type, that.type)
        && Objects.equals(data, that.data)
        && Objects.equals(elementId, that.elementId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(bpmnProcessId, elementId, type, data);
  }

  @Override
  public String toString() {
    return "InboundConnectorResponse{"
        + "bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", type='"
        + type
        + '\''
        + ", data="
        + data
        + ", elementId='"
        + elementId
        + '}';
  }
}
