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
import io.camunda.connector.runtime.core.Keywords;
import io.camunda.connector.runtime.core.Keywords.DeduplicationMode;
import io.camunda.connector.runtime.core.error.InvalidInboundConnectorDefinitionException;
import io.camunda.connector.runtime.core.inbound.correlation.ProcessCorrelationPoint;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Inbound connector definition implementation that also contains connector properties */
public record InboundConnectorElement(
    @JsonIgnore Map<String, String> rawProperties,
    ProcessCorrelationPoint correlationPoint,
    ProcessElement element) {

  private static final Logger LOG = LoggerFactory.getLogger(InboundConnectorElement.class);

  public String type() {
    return Optional.ofNullable(rawProperties.get(Keywords.INBOUND_TYPE_KEYWORD))
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Missing connector type property. The connector element template is not valid"));
  }

  public String tenantId() {
    return element.tenantId();
  }

  public String resultExpression() {
    return rawProperties.get(Keywords.RESULT_EXPRESSION_KEYWORD);
  }

  public String resultVariable() {
    return rawProperties.get(Keywords.RESULT_VARIABLE_KEYWORD);
  }

  public String activationCondition() {
    return Optional.ofNullable(rawProperties.get(Keywords.ACTIVATION_CONDITION_KEYWORD))
        .orElseGet(() -> rawProperties.get(Keywords.DEPRECATED_ACTIVATION_CONDITION_KEYWORD));
  }

  public String deduplicationId(List<String> deduplicationProperties) {
    LOG.debug("Computing deduplicationId for element {}", element.elementId());
    var deduplicationMode = rawProperties.get(Keywords.DEDUPLICATION_MODE_KEYWORD);
    if (deduplicationMode == null) {
      // legacy deployment, return a deterministic unique id
      LOG.debug("Missing deduplicationMode property, using legacy deduplicationId computation");
      return element.tenantId() + "-" + element.processDefinitionKey() + "-" + element.elementId();
    } else if (DeduplicationMode.AUTO.name().equals(deduplicationMode)) {
      // auto mode, compute deduplicationId from properties
      LOG.debug("Using deduplicationMode=AUTO, computing deduplicationId from properties");
      return computeDeduplicationId(deduplicationProperties);
    } else if (DeduplicationMode.MANUAL.name().equals(deduplicationMode)) {
      // manual mode, expect deduplicationId property
      LOG.debug("Using deduplicationMode=MANUAL, expecting deduplicationId property");
      return Optional.ofNullable(rawProperties.get(Keywords.DEDUPLICATION_ID_KEYWORD))
          .orElseThrow(
              () ->
                  new InvalidInboundConnectorDefinitionException(
                      "Missing deduplicationId property, expected a value due to deduplicationMode=MANUAL"));
    } else {
      throw new InvalidInboundConnectorDefinitionException(
          "Invalid deduplicationMode property, expected AUTO or MANUAL, but was "
              + deduplicationMode);
    }
  }

  private String computeDeduplicationId(List<String> deduplicationProperties) {
    List<String> propsToHash;
    if (!deduplicationProperties.isEmpty()) {
      propsToHash =
          deduplicationProperties.stream()
              .map(rawProperties::get)
              .filter(Objects::nonNull)
              .toList();
    } else {
      propsToHash = rawPropertiesWithoutKeywords().values().stream().toList();
    }
    if (propsToHash.isEmpty()) {
      throw new InvalidInboundConnectorDefinitionException(
          "Missing deduplication properties, expected at least one property to compute deduplicationId");
    }
    return tenantId() + "-" + element.bpmnProcessId() + "-" + Objects.hash(propsToHash);
  }

  public Map<String, String> rawPropertiesWithoutKeywords() {
    return rawProperties.entrySet().stream()
        .filter(e -> !Keywords.ALL_KEYWORDS.contains(e.getKey()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  // override to exclude rawProperties
  @Override
  public String toString() {
    return "InboundConnectorDefinitionImpl{"
        + ", correlationPoint="
        + correlationPoint
        + ", element="
        + element
        + '\''
        + '}';
  }
}
