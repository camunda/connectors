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
package io.camunda.connector.runtime.core.inbound.details;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import io.camunda.connector.runtime.core.inbound.ExecutableId;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import java.util.List;
import java.util.Map;

/** Group of inbound connector elements that share the same deduplication ID. */
public sealed interface InboundConnectorDetails {

  static InboundConnectorDetails of(
      String deduplicationId, List<InboundConnectorElement> groupedElements) {
    return InboundConnectorDetailsUtil.create(deduplicationId, groupedElements);
  }

  List<InboundConnectorElement> connectorElements();

  String type();

  String deduplicationId();

  default ExecutableId id() {
    return ExecutableId.fromDeduplicationId(deduplicationId());
  }

  String tenantId();

  String processDefinitionId();

  record InvalidInboundConnectorDetails(
      List<InboundConnectorElement> connectorElements,
      Throwable error,
      String tenantId,
      String deduplicationId,
      String type,
      String processDefinitionId)
      implements InboundConnectorDetails {}

  record ValidInboundConnectorDetails(
      String type,
      String tenantId,
      String deduplicationId,
      @JsonIgnore Map<String, String> rawPropertiesWithoutKeywords,
      List<InboundConnectorElement> connectorElements,
      String processDefinitionId)
      implements InboundConnectorDetails {

    /**
     * Whether this InboundConnectorDetails is compatible with another one. Two
     * InboundConnectorDetails are compatible if they have the same type, tenantId, deduplicationId,
     * are related to the same process definition, AND have the same properties. They can have
     * different connector elements. This is useful to know if we can update an existing inbound
     * connector if it has not been changed when a new process version is deployed.
     */
    public boolean isCompatibleWith(ValidInboundConnectorDetails other) {
      if (!this.type().equals(other.type())) {
        return false;
      }
      if (!this.tenantId().equals(other.tenantId())) {
        return false;
      }
      if (!this.deduplicationId().equals(other.deduplicationId())) {
        return false;
      }
      if (!this.processDefinitionId().equals(other.processDefinitionId())) {
        return false;
      }
      MapDifference<String, String> diff =
          Maps.difference(this.rawPropertiesWithoutKeywords, other.rawPropertiesWithoutKeywords);
      return diff.areEqual();
    }
  }
}
