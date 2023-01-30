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
package io.camunda.connector.api.inbound;

import java.util.Objects;

/**
 * Contains general information about the inbound correlation results.
 *
 * <p>This information is specific to the process correlation point type, e.g. subscription key and
 * message name in case of an IntermediateEvent target, or process definition key in case of a
 * StartEvent target.
 */
public abstract class InboundConnectorResult {

  protected String type;
  protected String id;
  protected Object responseData;

  protected InboundConnectorResult(String type, String id, Object responseData) {
    this.type = type;
    this.id = id;
    this.responseData = responseData;
  }

  /** Type of process correlation point, e.g. StartEvent or Message */
  public String getType() {
    return type;
  }

  /** ID of a process correlation point (unique within its type, see {@link #getType()} */
  public String getId() {
    return id;
  }

  /** Additional information related to Inbound Connector correlation result */
  public Object getResponseData() {
    return this.responseData;
  }

  @Override
  public String toString() {
    return "InboundConnectorResult{"
        + "type='"
        + type
        + '\''
        + ", id='"
        + id
        + '\''
        + ", responseData="
        + responseData
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    InboundConnectorResult result = (InboundConnectorResult) o;
    return Objects.equals(type, result.type)
        && Objects.equals(id, result.id)
        && Objects.equals(responseData, result.responseData);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, id, responseData);
  }
}
