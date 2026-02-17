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

import io.camunda.connector.generator.dsl.ElementTemplate.ElementTypeWrapper;
import io.camunda.connector.generator.dsl.ElementTemplate.Metadata;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeProperty;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeTaskDefinition;
import io.camunda.connector.generator.java.annotation.BpmnType;
import io.camunda.connector.generator.java.annotation.FeelMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Builder for creating an element template. */
public class ElementTemplateBuilder {

  protected final List<PropertyGroup> groups = new ArrayList<>();
  protected final List<Property> properties = new ArrayList<>();
  private final Mode mode;
  protected String id;
  protected String name;
  protected int version;
  protected ElementTemplateIcon icon;
  protected String documentationRef;
  protected String description;
  protected Engines engines;
  protected Metadata metadata;
  protected Set<String> appliesTo;
  protected BpmnType elementType;

  private ElementTemplateBuilder(Mode mode) {
    this.mode = mode;
  }

  public static ElementTemplateBuilder createOutbound() {
    return new ElementTemplateBuilder(Mode.OUTBOUND);
  }

  public static ElementTemplateBuilder createInbound() {
    return new ElementTemplateBuilder(Mode.INBOUND);
  }

  protected boolean isTypeAssigned() {
    return switch (mode) {
      case INBOUND ->
          properties.stream().anyMatch(property -> property.binding.equals(ZeebeProperty.TYPE));
      case OUTBOUND ->
          properties.stream()
              .anyMatch(property -> property.binding.equals(ZeebeTaskDefinition.TYPE));
    };
  }

  public ElementTemplateBuilder type(String type) {
    return type(type, false);
  }

  public ElementTemplateBuilder type(String type, boolean configurable) {
    Property property;
    switch (mode) {
      case INBOUND:
        if (configurable) {
          groups.addFirst(
              PropertyGroup.builder().id("connectorType").label("Connector type").build());
          property =
              StringProperty.builder()
                  .binding(ZeebeProperty.TYPE)
                  .value(type)
                  .id("connectorType")
                  .group("connectorType")
                  .feel(FeelMode.disabled)
                  .build();
        } else {
          property = HiddenProperty.builder().binding(ZeebeProperty.TYPE).value(type).build();
        }
        break;
      case OUTBOUND:
        if (configurable) {
          groups.addFirst(
              PropertyGroup.builder()
                  .id("taskDefinitionType")
                  .label("Task definition type")
                  .build());
          property =
              StringProperty.builder()
                  .binding(ZeebeTaskDefinition.TYPE)
                  .value(type)
                  .id("taskDefinitionType")
                  .group("taskDefinitionType")
                  .feel(FeelMode.disabled)
                  .build();
        } else {
          property = HiddenProperty.builder().binding(ZeebeTaskDefinition.TYPE).value(type).build();
        }
        break;
      default:
        throw new IllegalStateException("Unexpected builder mode value: " + mode);
    }
    properties.add(property);
    return this;
  }

  public ElementTemplateBuilder id(String id) {
    this.id = id;
    return this;
  }

  public ElementTemplateBuilder name(String name) {
    this.name = name;
    return this;
  }

  public ElementTemplateBuilder version(int version) {
    this.version = version;
    return this;
  }

  public ElementTemplateBuilder engines(Engines engines) {
    this.engines = engines;
    return this;
  }

  public ElementTemplateBuilder icon(ElementTemplateIcon icon) {
    this.icon = icon;
    return this;
  }

  public ElementTemplateBuilder documentationRef(String documentationRef) {
    this.documentationRef = documentationRef;
    return this;
  }

  public ElementTemplateBuilder description(String description) {
    this.description = description;
    return this;
  }

  public ElementTemplateBuilder metadata(Metadata metadata) {
    this.metadata = metadata;
    return this;
  }

  public ElementTemplateBuilder appliesTo(Set<BpmnType> appliesTo) {
    this.appliesTo = appliesTo.stream().map(BpmnType::getName).collect(Collectors.toSet());
    return this;
  }

  public ElementTemplateBuilder appliesTo(BpmnType... appliesTo) {
    this.appliesTo = Arrays.stream(appliesTo).map(BpmnType::getName).collect(Collectors.toSet());
    return this;
  }

  public ElementTemplateBuilder elementType(BpmnType elementType) {
    this.elementType = elementType;
    return this;
  }

  public ElementTemplateBuilder propertyGroups(PropertyGroup... groups) {
    this.groups.addAll(Arrays.asList(groups));
    this.properties.addAll(
        Arrays.stream(groups).flatMap(group -> group.properties().stream()).toList());
    return this;
  }

  public ElementTemplateBuilder propertyGroups(Collection<PropertyGroup> groups) {
    this.groups.addAll(groups);
    this.properties.addAll(groups.stream().flatMap(group -> group.properties().stream()).toList());
    return this;
  }

  public ElementTemplateBuilder properties(Property... properties) {
    this.properties.addAll(Arrays.asList(properties));
    return this;
  }

  public ElementTemplateBuilder properties(Collection<Property> properties) {
    this.properties.addAll(properties);
    return this;
  }

  public ElementTemplate build() {
    if (!isTypeAssigned()) {
      throw new IllegalStateException("type is not assigned");
    }
    return new ElementTemplate(
        id,
        name,
        version,
        documentationRef,
        engines,
        description,
        metadata,
        appliesTo,
        ElementTypeWrapper.from(elementType),
        groups,
        properties,
        icon);
  }

  private enum Mode {
    INBOUND,
    OUTBOUND
  }
}
