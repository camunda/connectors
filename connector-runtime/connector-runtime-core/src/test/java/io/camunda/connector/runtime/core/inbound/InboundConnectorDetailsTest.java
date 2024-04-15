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

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import io.camunda.connector.api.inbound.ProcessElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class InboundConnectorDetailsTest {

  @Test
  void minimallyValidElements() {
    // given
    List<InboundConnectorElement> elements = new ArrayList<>();
    elements.add(
        new InboundConnectorElement(
            Map.of("inbound.type", "type1", "deduplicationMode", "AUTO", "property1", "value1"),
            null,
            new ProcessElement("myProcess", 0, 0, "element1", "<default>")));
    elements.add(
        new InboundConnectorElement(
            Map.of("inbound.type", "type1", "deduplicationMode", "AUTO", "property1", "value1"),
            null,
            new ProcessElement("myProcess", 0, 0, "element2", "<default>")));

    // when & then
    assertDoesNotThrow(
        () ->
            new InboundConnectorDetails(elements.getFirst().deduplicationId(List.of()), elements));
  }

  @Test
  void notMatchingTypes_throwsException() {
    // given
    List<InboundConnectorElement> elements = new ArrayList<>();
    elements.add(
        new InboundConnectorElement(
            Map.of("inbound.type", "type1"),
            null,
            new ProcessElement("myProcess", 0, 0, "element1", "<default>")));

    elements.add(
        new InboundConnectorElement(
            Map.of("inbound.type", "type2"),
            null,
            new ProcessElement("myProcess", 0, 0, "element2", "<default>")));

    // when & then
    assertThatThrownBy(
            () ->
                new InboundConnectorDetails(
                    elements.getFirst().deduplicationId(List.of()), elements))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("All elements in a group must have the same type");
  }

  @Test
  void notMatchingTenantIds_throwsException() {
    // given
    List<InboundConnectorElement> elements = new ArrayList<>();
    elements.add(
        new InboundConnectorElement(
            Map.of(
                "inbound.type", "type1",
                "deduplicationMode", "MANUAL",
                "deduplicationId", "deduplicationId"),
            null,
            new ProcessElement("myProcess", 0, 0, "element1", "tenant1")));
    elements.add(
        new InboundConnectorElement(
            Map.of(
                "inbound.type", "type1",
                "deduplicationMode", "MANUAL",
                "deduplicationId", "deduplicationId"),
            null,
            new ProcessElement("myProcess", 0, 0, "element2", "tenant2")));

    // when & then
    assertThatThrownBy(
            () ->
                new InboundConnectorDetails(
                    elements.getFirst().deduplicationId(List.of()), elements))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("All elements in a group must have the same tenant ID");
  }

  @Test
  void notMatchingProperties_throwsExceptions() {
    // given
    List<InboundConnectorElement> elements = new ArrayList<>();
    elements.add(
        new InboundConnectorElement(
            Map.of(
                "inbound.type", "type1",
                "deduplicationMode", "MANUAL",
                "deduplicationId", "deduplicationId",
                "property1", "property1"),
            null,
            new ProcessElement("myProcess", 0, 0, "element1", "tenant")));

    elements.add(
        new InboundConnectorElement(
            Map.of(
                "inbound.type", "type1",
                "deduplicationMode", "MANUAL",
                "deduplicationId", "deduplicationId",
                "property2", "property2"),
            null,
            new ProcessElement("myProcess", 0, 0, "element2", "tenant")));

    // when & then
    assertThatThrownBy(
            () ->
                new InboundConnectorDetails(
                    elements.getFirst().deduplicationId(List.of()), elements))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "All elements in a group must have the same properties (excluding runtime-level properties)");
  }
}
