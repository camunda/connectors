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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@JsonPropertyOrder({
  "$schema",
  "name",
  "id",
  "description",
  "documentationRef",
  "version",
  "category",
  "appliesTo",
  "elementType",
  "groups",
  "properties"
})
@JsonInclude(Include.NON_NULL)
public record OutboundElementTemplate(
    String id,
    String name,
    int version,
    String documentationRef,
    String description,
    List<PropertyGroup> groups,
    List<Property> properties) {

  public OutboundElementTemplate {
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
    if (documentationRef == null) {
      errors.add("documentationRef is required");
    }
    if (description == null) {
      errors.add("description is required");
    }
    if (groups == null) {
      errors.add("groups is required");
    }
    if (properties == null) {
      errors.add("properties is required");
    }
    if (!errors.isEmpty()) {
      throw new IllegalArgumentException(String.join(", ", errors));
    }
  }

  @JsonProperty("$schema")
  public String schema() {
    return "https://unpkg.com/@camunda/zeebe-element-templates-json-schema/resources/schema.json";
  }

  @JsonProperty
  public Set<String> appliesTo() {
    return Set.of("bpmn:Task");
  }

  @JsonProperty
  public Map<String, String> elementType() {
    return Map.of("value", "bpmn:ServiceTask");
  }

  @JsonProperty
  public Map<String, String> category() {
    return Map.of("id", "connectors", "name", "Connectors");
  }

  public static OutboundElementTemplateBuilder builder() {
    return OutboundElementTemplateBuilder.create();
  }
}
