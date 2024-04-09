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
package io.camunda.connector.runtime.core.inbound;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.api.inbound.ProcessElement;
import java.util.List;
import java.util.Map;

/** Group of inbound connector elements that share the same deduplication ID. */
public record InboundConnectorDetails(
    String type,
    String tenantId,
    String deduplicationId,
    @JsonIgnore Map<String, String> rawPropertiesWithoutKeywords,
    List<InboundConnectorElement> connectorElements) {

  public InboundConnectorDetails(String deduplicationId, List<InboundConnectorElement> elements) {
    this(
        extractType(elements),
        extractTenantId(elements),
        deduplicationId,
        extractRawProperties(elements),
        elements);
  }

  private static String extractType(List<InboundConnectorElement> elements) {
    if (elements.stream().map(InboundConnectorElement::type).distinct().count() > 1) {
      throw new IllegalArgumentException("All elements in a group must have the same type");
    }
    return elements.getFirst().type();
  }

  private static String extractTenantId(List<InboundConnectorElement> elements) {
    if (elements.stream()
            .map(InboundConnectorElement::element)
            .map(ProcessElement::tenantId)
            .distinct()
            .count()
        > 1) {
      throw new IllegalArgumentException("All elements in a group must have the same tenant ID");
    }
    return elements.getFirst().element().tenantId();
  }

  private static Map<String, String> extractRawProperties(List<InboundConnectorElement> elements) {
    if (elements.stream()
            .map(InboundConnectorElement::rawPropertiesWithoutKeywords)
            .distinct()
            .count()
        > 1) {

      throw new IllegalArgumentException(
          "All elements in a group must have the same properties (excluding runtime-level properties)");
    }
    return elements.getFirst().rawProperties();
  }
}
