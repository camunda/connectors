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
package io.camunda.connector.impl.inbound;

import static io.camunda.connector.impl.Constants.INBOUND_TYPE_KEYWORD;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class InboundConnectorProperties {

  private final String type;
  private final Map<String, String> properties;

  private final ProcessCorrelationPoint correlationPoint;

  private final String bpmnProcessId;
  private final int version;
  private final long processDefinitionKey;
  private final String elementId;

  public InboundConnectorProperties(
      ProcessCorrelationPoint correlationPoint,
      Map<String, String> properties,
      String bpmnProcessId,
      int version,
      long processDefinitionKey,
      String elementId) {
    this.type = properties.get(INBOUND_TYPE_KEYWORD);
    this.properties = properties;
    this.correlationPoint = correlationPoint;
    this.bpmnProcessId = bpmnProcessId;
    this.version = version;
    this.processDefinitionKey = processDefinitionKey;
    this.elementId = elementId;
  }

  /** Shortcut to {@link ProcessCorrelationPoint#getId()} */
  public String getCorrelationPointId() {
    return correlationPoint.getId();
  }

  public String getType() {
    return type;
  }

  /**
   * Returns raw properties as a map.
   *
   * <p>NB: this method returns flat properties, e.g. it can contain occurences like {"foo.bar":
   * "value"} instead of {"foo": {"bar": "value"}}.
   *
   * <p>See also {@link #getPropertiesAsObjectMap()}.
   */
  public Map<String, String> getProperties() {
    return properties;
  }

  /**
   * Returns properties as object map.
   *
   * <p>Example: {"foo.bar": "value"} gets transformed into {"foo": {"bar": "value"}}
   *
   * <p>See also {@link #getProperties()}
   */
  public Map<String, Object> getPropertiesAsObjectMap() {
    return parsePropertiesToObjectMap(properties);
  }

  public ProcessCorrelationPoint getCorrelationPoint() {
    return correlationPoint;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public int getVersion() {
    return version;
  }

  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public String getElementId() {
    return elementId;
  }

  public String getProperty(String propertyName) {
    return properties.get(propertyName);
  }

  public String getRequiredProperty(String propertyName) {
    final String property = getProperty(propertyName);
    if (property == null) {
      throw new IllegalStateException(
          "Required inbound connector property '" + propertyName + "' is missing.");
    }
    return property;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    InboundConnectorProperties that = (InboundConnectorProperties) o;
    return version == that.version
        && processDefinitionKey == that.processDefinitionKey
        && Objects.equals(type, that.type)
        && Objects.equals(properties, that.properties)
        && Objects.equals(correlationPoint, that.correlationPoint)
        && Objects.equals(bpmnProcessId, that.bpmnProcessId)
        && Objects.equals(elementId, that.elementId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        type,
        properties,
        correlationPoint,
        bpmnProcessId,
        version,
        processDefinitionKey,
        elementId);
  }

  @Override
  public String toString() {
    return "InboundConnectorProperties{"
        + "type='"
        + type
        + '\''
        + ", properties="
        + properties
        + ", correlationPoint="
        + correlationPoint
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", version="
        + version
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", elementId='"
        + elementId
        + '}';
  }

  // if the property has a nested property, it will be treated as a map
  // e.g. "foo.bar = 1" will be treated as "foo": {"bar": "1"}
  private Map<String, Object> parsePropertiesToObjectMap(Map<String, String> properties) {
    final Map<String, Object> result = new HashMap<>();
    properties.forEach((key, value) -> handleProperty(result, key, value));
    return result;
  }

  @SuppressWarnings("unchecked")
  private void handleProperty(Map<String, Object> result, String key, String value) {
    final String[] split = key.split("\\.");
    if (split.length == 1) {
      result.put(key, value);
    } else {
      final String firstKey = split[0];
      final String nestedKey = key.substring(firstKey.length() + 1);
      final Map<String, Object> nestedMap =
          (Map<String, Object>) result.computeIfAbsent(firstKey, k -> new HashMap<>());
      nestedMap.put(nestedKey, value);
    }
  }
}
