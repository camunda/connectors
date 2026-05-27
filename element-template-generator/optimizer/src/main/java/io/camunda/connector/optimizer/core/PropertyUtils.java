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
package io.camunda.connector.optimizer.core;

import io.camunda.connector.generator.dsl.BooleanProperty;
import io.camunda.connector.generator.dsl.DropdownProperty;
import io.camunda.connector.generator.dsl.HiddenProperty;
import io.camunda.connector.generator.dsl.NumberProperty;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.dsl.PropertyBuilder;
import io.camunda.connector.generator.dsl.PropertyCondition;
import io.camunda.connector.generator.dsl.StringProperty;
import io.camunda.connector.generator.dsl.TextProperty;

/** Helpers for producing modified copies of {@link Property} objects. */
public final class PropertyUtils {

  private PropertyUtils() {}

  /** Returns a copy of {@code property} with a new id. */
  public static Property withId(Property property, String newId) {
    return copyToBuilder(property).id(newId).build();
  }

  /** Returns a copy of {@code property} with a new condition. */
  public static Property withCondition(Property property, PropertyCondition newCondition) {
    return copyToBuilder(property).condition(newCondition).build();
  }

  /** Returns a copy of {@code property} with a new value. */
  public static Property withValue(Property property, Object newValue) {
    return copyToBuilder(property).value(newValue).build();
  }

  /** Returns a copy of {@code property} with no condition (unconditional). */
  public static Property withoutCondition(Property property) {
    return copyToBuilder(property).condition(null).build();
  }

  /**
   * Copy {@code property} into a builder of the appropriate concrete type, preserving every field
   * as-set by the source.
   *
   * <p>{@link Property} is sealed, so the {@code switch} is exhaustive at compile time — adding a
   * new permitted subtype to the DSL will surface here as a compilation error.
   */
  private static PropertyBuilder copyToBuilder(Property property) {
    PropertyBuilder builder = builderForType(property);
    builder
        .id(property.getId())
        .label(property.getLabel())
        .description(property.getDescription())
        .feel(property.getFeel())
        .group(property.getGroup())
        .binding(property.getBinding())
        .condition(property.getCondition())
        .tooltip(property.getTooltip())
        .placeholder(property.getPlaceholder())
        .exampleValue(property.getExampleValue());

    if (property.isOptional() != null) {
      builder.optional(property.isOptional());
    }

    if (property.getGeneratedValue() != null) {
      builder.generatedValue();
    } else if (property.getValue() != null) {
      builder.value(property.getValue());
    }

    if (property.getConstraints() != null) {
      builder.constraints(property.getConstraints());
    }

    if (property instanceof DropdownProperty dropdown && dropdown.getChoices() != null) {
      ((DropdownProperty.DropdownPropertyBuilder) builder).choices(dropdown.getChoices());
    }

    return builder;
  }

  private static PropertyBuilder builderForType(Property property) {
    return switch (property) {
      case HiddenProperty ignored -> HiddenProperty.builder();
      case StringProperty ignored -> StringProperty.builder();
      case TextProperty ignored -> TextProperty.builder();
      case NumberProperty ignored -> NumberProperty.builder();
      case BooleanProperty ignored -> BooleanProperty.builder();
      case DropdownProperty ignored -> DropdownProperty.builder();
    };
  }
}
