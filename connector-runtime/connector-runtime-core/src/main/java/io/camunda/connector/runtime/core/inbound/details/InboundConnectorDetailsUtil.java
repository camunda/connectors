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

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import io.camunda.connector.api.inbound.ProcessElement;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.InvalidInboundConnectorDetails;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.ValidInboundConnectorDetails;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;

public class InboundConnectorDetailsUtil {

  static InboundConnectorDetails create(
      String deduplicationId, List<InboundConnectorElement> groupedElements) {
    if (CollectionUtils.isEmpty(groupedElements)) {
      throw new IllegalArgumentException("At least one element must be provided");
    }
    try {
      return new ValidInboundConnectorDetails(
          extractType(groupedElements),
          extractTenantId(groupedElements),
          deduplicationId,
          extractRawProperties(groupedElements),
          groupedElements);
    } catch (Exception e) {
      var anyElement = groupedElements.getFirst();
      var tenantId = anyElement.element().tenantId();
      var type = anyElement.type();
      return new InvalidInboundConnectorDetails(
          groupedElements, e, tenantId, deduplicationId, type);
    }
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

    var distinctPropertySets =
        elements.stream()
            .collect(Collectors.groupingBy(InboundConnectorElement::rawPropertiesWithoutKeywords));
    if (distinctPropertySets.size() > 1) {
      Set<String> divergingProperties = getDivergingProperties(distinctPropertySets);

      throw new IllegalArgumentException(
          "All elements in a group must have the same properties (excluding runtime-level properties). The following properties are different: "
              + String.join(", ", divergingProperties));
    }
    return elements.getFirst().rawProperties();
  }

  private static Set<String> getDivergingProperties(
      Map<Map<String, String>, List<InboundConnectorElement>> distinctPropertyMaps) {

    if (distinctPropertyMaps.size() <= 1) {
      return Set.of();
    }

    var representativeProps = distinctPropertyMaps.keySet().iterator().next();

    Set<String> divergingProperties = new HashSet<>();
    for (var entry : distinctPropertyMaps.entrySet()) {
      if (entry.getKey().equals(representativeProps)) {
        continue;
      }
      MapDifference<String, String> mapDifference =
          Maps.difference(representativeProps, entry.getKey());
      var entriesDiffering = mapDifference.entriesDiffering();
      divergingProperties.addAll(entriesDiffering.keySet());
      divergingProperties.addAll(mapDifference.entriesOnlyOnLeft().keySet());
      divergingProperties.addAll(mapDifference.entriesOnlyOnRight().keySet());
    }
    return divergingProperties;
  }
}
