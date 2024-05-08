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

  String tenantId();

  record InvalidInboundConnectorDetails(
      List<InboundConnectorElement> connectorElements,
      Throwable error,
      String tenantId,
      String deduplicationId,
      String type)
      implements InboundConnectorDetails {}

  record ValidInboundConnectorDetails(
      String type,
      String tenantId,
      String deduplicationId,
      @JsonIgnore Map<String, String> rawPropertiesWithoutKeywords,
      List<InboundConnectorElement> connectorElements)
      implements InboundConnectorDetails {}
}
