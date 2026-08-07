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
import io.camunda.connector.generator.dsl.PropertyCondition;
import io.camunda.connector.generator.dsl.StringProperty;
import io.camunda.connector.generator.dsl.TextProperty;

/**
 * Helpers for producing modified copies of {@link Property} objects.
 *
 * <p>Each {@code with*} call dispatches on the sealed {@link Property} hierarchy and invokes the
 * matching public constructor directly. We deliberately bypass the subtype builders because {@code
 * StringPropertyBuilder.build()} and {@code TextPropertyBuilder.build()} default a null {@code
 * feel} to {@link io.camunda.connector.generator.java.annotation.FeelMode#optional} — a behaviour
 * that's correct for the original generation step but would silently rewrite {@code feel} on every
 * property the optimizer touches.
 */
public final class PropertyUtils {

  private PropertyUtils() {}

  /** Returns a copy of {@code property} with a new id. */
  public static Property withId(Property property, String newId) {
    return rebuild(property, newId, property.getCondition(), property.getValue());
  }

  /** Returns a copy of {@code property} with a new condition. */
  public static Property withCondition(Property property, PropertyCondition newCondition) {
    return rebuild(property, property.getId(), newCondition, property.getValue());
  }

  /** Returns a copy of {@code property} with a new value. */
  public static Property withValue(Property property, Object newValue) {
    return rebuild(property, property.getId(), property.getCondition(), newValue);
  }

  /** Returns a copy of {@code property} with no condition (unconditional). */
  public static Property withoutCondition(Property property) {
    return rebuild(property, property.getId(), null, property.getValue());
  }

  /**
   * Construct a new Property of the same concrete subtype with the given id/condition/value, and
   * every other field preserved by direct constructor invocation.
   *
   * <p>{@link Property} is sealed; the switch is exhaustive at compile time so a new permitted
   * subtype surfaces here as a compile error.
   */
  private static Property rebuild(
      Property source, String id, PropertyCondition condition, Object value) {
    return switch (source) {
      case HiddenProperty p ->
          new HiddenProperty(
              id,
              p.getLabel(),
              p.getDescription(),
              p.isOptional(),
              (String) value,
              p.getGeneratedValue(),
              p.getConstraints(),
              p.getFeel(),
              p.getGroup(),
              p.getBinding(),
              condition);
      case StringProperty p ->
          new StringProperty(
              id,
              p.getLabel(),
              p.getDescription(),
              p.isOptional(),
              (String) value,
              p.getGeneratedValue(),
              p.getConstraints(),
              p.getFeel(),
              p.getGroup(),
              p.getBinding(),
              condition,
              p.getTooltip(),
              p.getPlaceholder(),
              p.getExampleValue(),
              p.getLanguage());
      case TextProperty p ->
          new TextProperty(
              id,
              p.getLabel(),
              p.getDescription(),
              p.isOptional(),
              (String) value,
              p.getGeneratedValue(),
              p.getConstraints(),
              p.getFeel(),
              p.getGroup(),
              p.getBinding(),
              condition,
              p.getTooltip(),
              p.getPlaceholder(),
              p.getExampleValue(),
              p.getLanguage());
      case NumberProperty p ->
          new NumberProperty(
              id,
              p.getLabel(),
              p.getDescription(),
              p.isOptional(),
              (Number) value,
              p.getGeneratedValue(),
              p.getConstraints(),
              p.getFeel(),
              p.getGroup(),
              p.getBinding(),
              condition,
              p.getTooltip(),
              p.getExampleValue());
      case BooleanProperty p ->
          new BooleanProperty(
              id,
              p.getLabel(),
              p.getDescription(),
              p.isOptional(),
              (Boolean) value,
              p.getGeneratedValue(),
              p.getConstraints(),
              p.getFeel(),
              p.getGroup(),
              p.getBinding(),
              condition,
              p.getTooltip(),
              p.getExampleValue());
      case DropdownProperty p ->
          new DropdownProperty(
              id,
              p.getLabel(),
              p.getDescription(),
              p.isOptional(),
              (String) value,
              p.getGeneratedValue(),
              p.getConstraints(),
              p.getFeel(),
              p.getGroup(),
              p.getBinding(),
              condition,
              p.getTooltip(),
              p.getChoices(),
              p.getExampleValue());
    };
  }
}
