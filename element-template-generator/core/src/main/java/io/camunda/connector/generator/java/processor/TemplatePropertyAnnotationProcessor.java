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
package io.camunda.connector.generator.java.processor;

import static io.camunda.connector.generator.java.util.TemplatePropertiesUtil.isOutbound;

import io.camunda.connector.generator.dsl.*;
import io.camunda.connector.generator.dsl.PropertyConstraints;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.EqualsBoolean;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NestedPropertyCondition;
import io.camunda.connector.generator.java.util.TemplateGenerationContext;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

/** {@link TemplateProperty} annotation processor */
public class TemplatePropertyAnnotationProcessor implements AnnotationProcessor {

  public static PropertyCondition transformToCondition(
      TemplateProperty.PropertyCondition conditionAnnotation) {

    if (!conditionAnnotation.equals().isBlank()) {
      return new PropertyCondition.Equals(
          conditionAnnotation.property(), conditionAnnotation.equals());
    } else if (conditionAnnotation.equalsBoolean() != EqualsBoolean.NULL) {
      return new PropertyCondition.Equals(
          conditionAnnotation.property(), conditionAnnotation.equalsBoolean().toBoolean());
    } else if (conditionAnnotation.oneOf().length > 0) {
      return new PropertyCondition.OneOf(
          conditionAnnotation.property(), Arrays.asList(conditionAnnotation.oneOf()));
    } else if (conditionAnnotation.allMatch().length > 0) {
      return new PropertyCondition.AllMatch(
          Arrays.stream(conditionAnnotation.allMatch())
              .map(TemplatePropertyAnnotationProcessor::transformToNestedCondition)
              .toList());
    } else {
      // isActive always has a value, so we consider it is selected if nothing else is set
      return new PropertyCondition.IsActive(
          conditionAnnotation.property(), conditionAnnotation.isActive());
    }
  }

  public static PropertyCondition transformToNestedCondition(
      NestedPropertyCondition conditionAnnotation) {
    validateCondition(
        conditionAnnotation.property(),
        conditionAnnotation.equals(),
        conditionAnnotation.equalsBoolean(),
        conditionAnnotation.oneOf(),
        new NestedPropertyCondition[] {});
    if (!conditionAnnotation.equals().isBlank()) {
      return new PropertyCondition.Equals(
          conditionAnnotation.property(), conditionAnnotation.equals());
    } else if (conditionAnnotation.equalsBoolean() != EqualsBoolean.NULL) {
      return new PropertyCondition.Equals(
          conditionAnnotation.property(), conditionAnnotation.equalsBoolean().toBoolean());
    } else if (conditionAnnotation.oneOf().length > 0) {
      return new PropertyCondition.OneOf(
          conditionAnnotation.property(), Arrays.asList(conditionAnnotation.oneOf()));
    } else {
      // isActive always has a value, so we consider it is selected if nothing else is set
      return new PropertyCondition.IsActive(
          conditionAnnotation.property(), conditionAnnotation.isActive());
    }
  }

  private static void validateCondition(
      String property,
      String equals,
      EqualsBoolean equalsBoolean,
      String[] oneOf,
      NestedPropertyCondition[] allMatch) {
    var equalsSet = !equals.isBlank();
    var equalsBooleanSet = !equalsBoolean.equals(EqualsBoolean.NULL);
    var oneOfSet = oneOf != null && oneOf.length > 0;
    var allMatchSet = allMatch != null && allMatch.length > 0;
    // equalsBoolean always has a value, so it's not included in the check
    // if everything else is not set, we consider it an equalsBoolean condition

    if (equalsSet && equalsBooleanSet
        || equalsSet && oneOfSet
        || equalsSet && allMatchSet
        || oneOfSet && allMatchSet
        || oneOfSet && equalsBooleanSet
        || allMatchSet && equalsBooleanSet) {
      throw new IllegalStateException(
          "Condition must have only one of 'equals', 'equalsBoolean', 'oneOf', 'isActive', or 'allMatch' set");
    }
    if (equalsSet && property.isBlank()) {
      throw new IllegalStateException("Condition 'equals' must have 'property' set");
    }

    if (oneOfSet && property.isBlank()) {
      throw new IllegalStateException("Condition 'oneOf' must have 'property' set");
    }

    if (allMatchSet && !property.isBlank()) {
      throw new IllegalStateException("Condition 'allMatch' must not have 'property' set");
    }

    if (!equalsSet && !oneOfSet && !allMatchSet && property.isBlank()) {
      throw new IllegalStateException("Condition 'isActive' must have 'property' set");
    }
  }

  @Override
  public void process(
      AnnotatedElement field,
      Class<?> type,
      PropertyBuilder builder,
      final TemplateGenerationContext context) {
    var annotation = field.getAnnotation(TemplateProperty.class);
    if (annotation == null) {
      return;
    }
    builder.optional(AnnotationProcessor.isOptional(field));

    switch (builder) {
      case DropdownProperty.DropdownPropertyBuilder ignored -> {}
      case NumberProperty.NumberPropertyBuilder ignored -> manageFeelMode(annotation, builder);
      case BooleanProperty.BooleanPropertyBuilder ignored -> manageFeelMode(annotation, builder);
      default -> {
        if (annotation.feel() == FeelMode.system_default) {
          builder.feel(determineDefaultFeelModeBasedOnContext(context));
        } else {
          builder.feel(annotation.feel());
        }
      }
    }

    if (!annotation.label().isBlank()) {
      builder.label(annotation.label());
    }
    if (!annotation.description().isBlank()) {
      builder.description(annotation.description());
    }
    if (!annotation.defaultValue().isBlank()) {
      builder.value(getValue(annotation, type, isOutbound(context)));
    }
    if (!annotation.group().isBlank()) {
      builder.group(annotation.group());
    }
    builder.condition(buildCondition(annotation.condition()));
    builder.constraints(buildConstraints(annotation, builder.build().getConstraints()));
  }

  public static Object getValue(TemplateProperty annotation, Class<?> type, boolean isOutbound) {
    var defaultValue = annotation.defaultValue();
    return switch (annotation.defaultValueType()) {
      case Boolean -> Boolean.parseBoolean(defaultValue);
      case String -> defaultValue;
      case Number -> {
        if (isOutbound) {
          yield parseNumber(defaultValue, type);
        } else {
          yield defaultValue;
        }
      }
      default ->
          throw new IllegalStateException("Unexpected value: " + annotation.defaultValueType());
    };
  }

  private void manageFeelMode(TemplateProperty annotation, PropertyBuilder builder) {
    if (annotation.feel() == FeelMode.disabled) {
      throw new IllegalStateException(
          "`disabled` is not a valid feel property for " + annotation.type());
    } else if (annotation.feel() == FeelMode.system_default) {
      builder.feel(FeelMode.staticFeel);
    } else {
      builder.feel(annotation.feel());
    }
  }

  private static Number parseNumber(String value, Class<?> type) {
    try {
      return (Number) type.getDeclaredConstructor(String.class).newInstance(value);
    } catch (InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | NoSuchMethodException e) {
      throw new IllegalStateException(e.getMessage(), e.getCause());
    }
  }

  private FeelMode determineDefaultFeelModeBasedOnContext(final TemplateGenerationContext context) {
    return context instanceof TemplateGenerationContext.Inbound
        ? FeelMode.disabled
        : FeelMode.optional;
  }

  public static PropertyCondition buildCondition(
      TemplateProperty.PropertyCondition conditionAnnotation) {
    if (conditionAnnotation.property().isBlank() && conditionAnnotation.allMatch().length == 0) {
      return null;
    }
    validateCondition(
        conditionAnnotation.property(),
        conditionAnnotation.equals(),
        conditionAnnotation.equalsBoolean(),
        conditionAnnotation.oneOf(),
        conditionAnnotation.allMatch());
    return transformToCondition(conditionAnnotation);
  }

  private PropertyConstraints buildConstraints(
      TemplateProperty propertyAnnotation, PropertyConstraints propertyConstraints) {
    var constraintsAnnotation = propertyAnnotation.constraints();
    if (!constraintsAnnotation.notEmpty()
        && constraintsAnnotation.maxLength() == Integer.MAX_VALUE
        && constraintsAnnotation.minLength() == Integer.MIN_VALUE
        && constraintsAnnotation.pattern().value().isBlank()) {
      return propertyConstraints;
    }
    var builder = PropertyConstraints.builder(propertyConstraints);
    if (constraintsAnnotation.notEmpty()) {
      builder.notEmpty(true);
    }
    if (constraintsAnnotation.maxLength() != Integer.MAX_VALUE) {
      builder.maxLength(constraintsAnnotation.maxLength());
    }
    if (constraintsAnnotation.minLength() != Integer.MIN_VALUE) {
      builder.minLength(constraintsAnnotation.minLength());
    }
    if (!constraintsAnnotation.pattern().value().isBlank()) {
      if (!constraintsAnnotation.notEmpty() && propertyAnnotation.optional()) {
        builder.notEmpty(false);
      }
      builder.pattern(
          new PropertyConstraints.Pattern(
              constraintsAnnotation.pattern().value(), constraintsAnnotation.pattern().message()));
    }
    return builder.build();
  }
}
