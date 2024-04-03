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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.api.inbound.ProcessElement;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.Keywords;
import io.camunda.connector.runtime.core.error.InvalidInboundConnectorDefinitionException;
import io.camunda.connector.runtime.core.inbound.correlation.MessageCorrelationPoint.StandaloneMessageCorrelationPoint;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

public class InboundConnectorElementTest {

  @Test
  void rawProperties_notSerializedAsJson() throws JsonProcessingException {
    // given
    var testObj =
        new InboundConnectorElement(
            Map.of("auth", "abc"),
            new StandaloneMessageCorrelationPoint("", "", null),
            new ProcessElement("myProcess", 0, 0, "element1"),
            "<default>");

    // when
    var result = ConnectorsObjectMapperSupplier.DEFAULT_MAPPER.writeValueAsString(testObj);

    // then
    assertThat(result).doesNotContain("auth", "abc");
  }

  @Test
  void rawProperties_notPartOfToString() {
    // given
    var testObj =
        new InboundConnectorElement(
            Map.of("auth", "abc"),
            new StandaloneMessageCorrelationPoint("", "", null),
            new ProcessElement("myProcess", 0, 0, "element1"),
            "<default>");

    // when
    var result = testObj.toString();

    // then
    assertThat(result).doesNotContain("auth", "abc");
  }

  @Test
  void connectorType_present() {
    // given
    var testObj =
        new InboundConnectorElement(
            Map.of("inbound.type", "test"),
            new StandaloneMessageCorrelationPoint("", "", null),
            new ProcessElement("myProcess", 0, 0, "element1"),
            "<default>");

    // when
    var result = testObj.type();

    // then
    assertThat(result).isEqualTo("test");
  }

  @Test
  void connectorType_absent() {
    // given
    var testObj =
        new InboundConnectorElement(
            Map.of(),
            new StandaloneMessageCorrelationPoint("", "", null),
            new ProcessElement("", 0, 0, ""),
            "");

    // when && then
    assertThatThrownBy(testObj::type)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Missing connector type property. The connector element template is not valid");
  }

  @Test
  void deduplicationId_autoMode() {
    // given
    var testObj =
        new InboundConnectorElement(
            Map.of("inbound.type", "test", "deduplicationMode", "AUTO", "property", "value"),
            new StandaloneMessageCorrelationPoint("", "", null),
            new ProcessElement("myProcess", 0, 0, "element1"),
            "");

    var testObjWithDifferentProperties =
        new InboundConnectorElement(
            Map.of("inbound.type", "test", "deduplicationMode", "AUTO", "property", "value2"),
            new StandaloneMessageCorrelationPoint("", "", null),
            new ProcessElement("myProcess", 0, 0, "element1"),
            "");

    var testObjWithSameProperties =
        new InboundConnectorElement(
            Map.of("inbound.type", "test1", "deduplicationMode", "AUTO", "property", "value"),
            new StandaloneMessageCorrelationPoint("", "", null),
            new ProcessElement("myProcess", 0, 0, "element1"),
            "");

    // when
    var result = testObj.deduplicationId();

    // then
    assertThat(result).isNotBlank();
    assertThat(testObj.deduplicationId()).isEqualTo(testObj.deduplicationId());
    assertThat(testObj.deduplicationId())
        .isNotEqualTo(testObjWithDifferentProperties.deduplicationId());
  }

  @Test
  void deduplicationId_manualMode() {
    // given
    var testObj =
        new InboundConnectorElement(
            Map.of("inbound.type", "test", "deduplicationMode", "MANUAL", "deduplicationId", "id"),
            new StandaloneMessageCorrelationPoint("", "", null),
            new ProcessElement("myProcess", 0, 0, "element1"),
            "");

    // when
    var result = testObj.deduplicationId();

    // then
    assertThat(result).isEqualTo("id");
  }

  @Test
  void deduplicationId_manualMode_noId() {
    // given
    var testObj =
        new InboundConnectorElement(
            Map.of("inbound.type", "test", "deduplicationMode", "MANUAL"),
            new StandaloneMessageCorrelationPoint("", "", null),
            new ProcessElement("myProcess", 0, 0, "element1"),
            "");

    // when && then
    assertThatThrownBy(testObj::deduplicationId)
        .isInstanceOf(InvalidInboundConnectorDefinitionException.class)
        .hasMessage(
            "Missing deduplicationId property, expected a value due to deduplicationMode=MANUAL");
  }

  @Test
  void deduplicationId_legacyMode() {
    // given
    var testObj =
        new InboundConnectorElement(
            Map.of("inbound.type", "test"),
            new StandaloneMessageCorrelationPoint("", "", null),
            new ProcessElement("myProcess", 0, 42L, "myElement"),
            "tenant");

    // when
    var result = testObj.deduplicationId();

    // then
    assertThat(result).isEqualTo("tenant-42-myElement");
  }

  @Test
  void resultExpression() {
    // given
    var testObj =
        new InboundConnectorElement(
            Map.of("inbound.type", "test", "resultExpression", "expression"),
            new StandaloneMessageCorrelationPoint("", "", null),
            new ProcessElement("myProcess", 0, 0, "element1"),
            "");

    // when
    var result = testObj.resultExpression();

    // then
    assertThat(result).isEqualTo("expression");
  }

  @Test
  void resultVariable() {
    // given
    var testObj =
        new InboundConnectorElement(
            Map.of("inbound.type", "test", "resultVariable", "variable"),
            new StandaloneMessageCorrelationPoint("", "", null),
            new ProcessElement("myProcess", 0, 0, "element1"),
            "");

    // when
    var result = testObj.resultVariable();

    // then
    assertThat(result).isEqualTo("variable");
  }

  @Test
  void activationCondition() {
    // given
    var testObj =
        new InboundConnectorElement(
            Map.of("inbound.type", "test", "activationCondition", "condition"),
            new StandaloneMessageCorrelationPoint("", "", null),
            new ProcessElement("myProcess", 0, 0, "element1"),
            "");

    // when
    var result = testObj.activationCondition();

    // then
    assertThat(result).isEqualTo("condition");
  }

  @Test
  void activationCondition_deprecated() {
    // given
    var testObj =
        new InboundConnectorElement(
            Map.of("inbound.type", "test", "inbound.activationCondition", "condition"),
            new StandaloneMessageCorrelationPoint("", "", null),
            new ProcessElement("myProcess", 0, 0, "element1"),
            "");

    // when
    var result = testObj.activationCondition();

    // then
    assertThat(result).isEqualTo("condition");
  }

  @Test
  void rawPropertiesWithoutKeywords() {
    // given
    var keywordProps =
        Keywords.ALL_KEYWORDS.stream()
            .map(k -> Map.entry(k, "value"))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    var withCustomProps = new HashMap<>(keywordProps);
    withCustomProps.put("property", "value");
    var testObj =
        new InboundConnectorElement(
            withCustomProps,
            new StandaloneMessageCorrelationPoint("", "", null),
            new ProcessElement("myProcess", 0, 0, "element1"),
            "");

    // when
    var result = testObj.rawPropertiesWithoutKeywords();

    // then
    assertThat(result).containsOnlyKeys("property");
  }
}
