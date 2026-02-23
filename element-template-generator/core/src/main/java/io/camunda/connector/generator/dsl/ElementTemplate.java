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
package io.camunda.connector.generator.dsl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.camunda.connector.generator.java.annotation.BpmnType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@JsonPropertyOrder({
  "$schema",
  "name",
  "id",
  "description",
  "metadata",
  "documentationRef",
  "version",
  "category",
  "appliesTo",
  "elementType",
  "engines",
  "groups",
  "properties"
})
@JsonInclude(Include.NON_NULL)
public record ElementTemplate(
    String id,
    String name,
    int version,
    String documentationRef,
    Engines engines,
    String description,
    Metadata metadata,
    Set<String> appliesTo,
    ElementTypeWrapper elementType,
    List<PropertyGroup> groups,
    List<Property> properties,
    ElementTemplateIcon icon) {

  static final String SCHEMA_FIELD_NAME = "$schema";
  static final String SCHEMA_URL =
      "https://unpkg.com/@camunda/zeebe-element-templates-json-schema/resources/schema.json";

  public ElementTemplate {
    List<String> errors = new ArrayList<>();
    if (id == null) {
      errors.add("id is required");
    }
    if (name == null) {
      errors.add("name is required");
    }
    if (version < 0) {
      errors.add("version cannot be negative");
    }
    if (appliesTo == null || appliesTo.isEmpty() || appliesTo.stream().allMatch(String::isBlank)) {
      errors.add("appliesTo must be defined");
    }
    if (elementType == null || elementType.value == null || elementType.value.isBlank()) {
      errors.add("elementType must be defined");
    }
    if (groups == null) {
      errors.add("groups is required");
    }
    if (properties == null) {
      errors.add("properties is required");
    } else {
      Set<String> propIdOccurrences = new HashSet<>();
      for (var property : properties) {
        if (property.id == null) {
          continue;
        }
        if (propIdOccurrences.contains(property.id)) {
          errors.add("duplicate property " + property.id);
        }
        propIdOccurrences.add(property.id);
      }
    }

    if (!errors.isEmpty()) {
      throw new IllegalArgumentException(String.join(", ", errors));
    }
    if (icon != null && !icon.contents().matches("^(data):.*")) {
      throw new IllegalArgumentException("icon contents must be base64 encoded");
    }
  }

  public static ElementTemplateBuilder builderForOutbound() {
    return ElementTemplateBuilder.createOutbound();
  }

  public static ElementTemplateBuilder builderForInbound() {
    return ElementTemplateBuilder.createInbound();
  }

  @JsonProperty
  public ElementTemplateCategory category() {
    return ElementTemplateCategory.CONNECTORS;
  }

  @JsonProperty(SCHEMA_FIELD_NAME)
  public String schema() {
    return SCHEMA_URL;
  }

  public record Metadata(String[] keywords) {}

  @JsonInclude(Include.NON_NULL)
  public record ElementTypeWrapper(
      String value, String eventDefinition, @JsonIgnore BpmnType originalType) {

    public static ElementTypeWrapper from(BpmnType value) {
      var haveEventDefinition =
          Set.of(
              BpmnType.INTERMEDIATE_CATCH_EVENT,
              BpmnType.INTERMEDIATE_THROW_EVENT,
              BpmnType.MESSAGE_START_EVENT,
              BpmnType.MESSAGE_END_EVENT,
              BpmnType.BOUNDARY_EVENT);
      var messageEventDefinition = "bpmn:MessageEventDefinition";

      return new ElementTypeWrapper(
          value.getName(),
          haveEventDefinition.contains(value) ? messageEventDefinition : null,
          value);
    }
  }
}
