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
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeProperty;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeTaskDefinition;
import io.camunda.connector.generator.java.annotation.BpmnType;
import io.camunda.connector.generator.java.annotation.FeelMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Builder for creating an element template. */
public class ElementTemplateBuilder {

  protected final List<PropertyGroup> groups = new ArrayList<>();
  protected final List<Property> properties = new ArrayList<>();
  protected final List<Step> steps = new ArrayList<>();
  protected final List<Preset> presets = new ArrayList<>();
  private final Mode mode;
  protected String id;
  protected String name;
  protected long version;
  protected ElementTemplateCategory category = ElementTemplateCategory.CONNECTORS;
  protected ElementTemplateIcon icon;
  protected String documentationRef;
  protected String description;
  protected Engines engines;
  protected String[] keywords;
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

  /**
   * Seeds a new builder from an existing element template, copying its metadata, groups,
   * properties, steps, and presets. Intended for deriving one connector's template from another's
   * generated template (e.g. reusing HTTP JSON's authentication properties for a connector that
   * executes as an HTTP JSON task under the hood) -- follow up with {@link
   * #removeProperties(Predicate)}, {@link #removePropertyGroups(Predicate)}, and {@link
   * #replaceProperty(Property)} to adjust what was inherited, and {@link #properties(Property...)}
   * / {@link #propertyGroups(PropertyGroup...)} to layer on what's new.
   */
  public static ElementTemplateBuilder from(ElementTemplate base) {
    Objects.requireNonNull(base, "base must not be null");
    boolean outbound =
        base.properties().stream().anyMatch(p -> p.binding instanceof ZeebeTaskDefinition);
    ElementTemplateBuilder builder =
        new ElementTemplateBuilder(outbound ? Mode.OUTBOUND : Mode.INBOUND);
    builder.id = base.id();
    builder.name = base.name();
    builder.version = base.version();
    builder.category = base.category();
    builder.documentationRef = base.documentationRef();
    builder.engines = base.engines();
    builder.description = base.description();
    builder.keywords = base.keywords();
    builder.appliesTo = base.appliesTo();
    builder.elementType = base.elementType() == null ? null : base.elementType().originalType();
    builder.icon = base.icon();
    builder.groups.addAll(base.groups());
    builder.properties.addAll(base.properties());
    if (base.steps() != null) {
      builder.steps.addAll(base.steps());
    }
    if (base.presets() != null) {
      builder.presets.addAll(base.presets());
    }
    return builder;
  }

  /**
   * Removes every property matching {@code predicate}. Use after {@link #from(ElementTemplate)} to
   * prune an inherited base template before layering on new properties.
   */
  public ElementTemplateBuilder removeProperties(Predicate<Property> predicate) {
    properties.removeIf(predicate);
    return this;
  }

  /**
   * Removes every property group matching {@code predicate}. See {@link
   * #removeProperties(Predicate)}.
   */
  public ElementTemplateBuilder removePropertyGroups(Predicate<PropertyGroup> predicate) {
    groups.removeIf(predicate);
    return this;
  }

  /**
   * Replaces the inherited property sharing {@code replacement}'s id (if any) with {@code
   * replacement} at the same list position, or simply appends it if no such property exists yet.
   * Preserving position (rather than removing and re-appending) matters because property order is
   * significant: a property's {@code condition} may only reference a property appearing earlier in
   * the list. Use to override a single inherited property -- e.g. narrowing a Dropdown's choices --
   * without rebuilding the rest of an inherited base template.
   */
  public ElementTemplateBuilder replaceProperty(Property replacement) {
    Objects.requireNonNull(replacement, "replacement must not be null");
    int index = -1;
    for (int i = 0; i < properties.size(); i++) {
      if (Objects.equals(properties.get(i).id, replacement.id)) {
        index = i;
        break;
      }
    }
    if (index >= 0) {
      properties.set(index, replacement);
    } else {
      properties.add(replacement);
    }
    return this;
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

  public ElementTemplateBuilder version(long version) {
    this.version = version;
    return this;
  }

  public ElementTemplateBuilder category(ElementTemplateCategory category) {
    this.category = category;
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

  public ElementTemplateBuilder keywords(String[] keywords) {
    this.keywords = keywords;
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

  public ElementTemplateBuilder steps(Collection<Step> steps) {
    this.steps.addAll(steps);
    return this;
  }

  public ElementTemplateBuilder presets(Collection<Preset> presets) {
    this.presets.addAll(presets);
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
        category,
        documentationRef,
        engines,
        description,
        keywords,
        appliesTo,
        ElementTypeWrapper.from(elementType),
        groups,
        properties,
        icon,
        steps.isEmpty() ? null : steps,
        presets.isEmpty() ? null : presets);
  }

  private enum Mode {
    INBOUND,
    OUTBOUND
  }
}
