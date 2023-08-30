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
    @JsonProperty String id,
    @JsonProperty String name,
    @JsonProperty int version,
    @JsonProperty String documentationRef,
    @JsonProperty String description,
    @JsonProperty List<PropertyGroup> groups,
    @JsonProperty List<Property> properties)
    implements ElementTemplateBase {

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

  @JsonProperty
  public Set<BpmnType> appliesTo() {
    return Set.of(BpmnType.TASK);
  }

  @JsonProperty
  public ElementType elementType() {
    return new ElementType(BpmnType.SERVICE_TASK);
  }

  @JsonProperty
  public ElementTemplateCategory category() {
    return ElementTemplateCategory.CONNECTORS;
  }

  public static OutboundElementTemplateBuilder builder() {
    return OutboundElementTemplateBuilder.create();
  }

  public record ElementType(@JsonProperty BpmnType value) {}
}
