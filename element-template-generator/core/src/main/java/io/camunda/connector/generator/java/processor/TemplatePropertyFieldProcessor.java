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

import io.camunda.connector.generator.dsl.DropdownProperty.DropdownPropertyBuilder;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.dsl.PropertyBuilder;
import io.camunda.connector.generator.dsl.PropertyCondition;
import io.camunda.connector.generator.dsl.PropertyConstraints;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NestedPropertyCondition;
import io.camunda.connector.generator.java.util.TemplateGenerationContext;
import java.lang.reflect.Field;
import java.util.Arrays;

/** {@link TemplateProperty} annotation processor */
public class TemplatePropertyFieldProcessor implements FieldProcessor {

  @Override
  public void process(
      Field field, PropertyBuilder builder, final TemplateGenerationContext context) {
    var annotation = field.getAnnotation(TemplateProperty.class);
    if (annotation == null) {
      return;
    }
    builder.optional(annotation.optional());

    if (!(builder instanceof DropdownPropertyBuilder)) {
      if (annotation.feel() == Property.FeelMode.system_default) {
        builder.feel(determineDefaultFeelModeBasedOnContext(context));
      } else {
        builder.feel(annotation.feel());
      }
    }

    if (!annotation.label().isBlank()) {
      builder.label(annotation.label());
    }
    if (!annotation.description().isBlank()) {
      builder.description(annotation.description());
    }
    if (!annotation.defaultValue().isBlank()) {
      var value = annotation.defaultValue();
      switch (annotation.defaultValueType()) {
        case Boolean:
          builder.value(Boolean.parseBoolean(value));
          break;
        case String:
          builder.value(value);
          break;
        default:
          throw new IllegalStateException("Unexpected value: " + annotation.defaultValueType());
      }
    }
    if (!annotation.group().isBlank()) {
      builder.group(annotation.group());
    }
    builder.condition(buildCondition(annotation));
    builder.constraints(buildConstraints(annotation));
  }

  private Property.FeelMode determineDefaultFeelModeBasedOnContext(
      final TemplateGenerationContext context) {
    return context instanceof TemplateGenerationContext.Inbound
        ? Property.FeelMode.disabled
        : Property.FeelMode.optional;
  }

  private PropertyCondition buildCondition(TemplateProperty propertyAnnotation) {
    var conditionAnnotation = propertyAnnotation.condition();
    if (conditionAnnotation.property().isBlank() && conditionAnnotation.allMatch().length == 0) {
      return null;
    }
    validateCondition(
        conditionAnnotation.property(),
        conditionAnnotation.equals(),
        conditionAnnotation.oneOf(),
        conditionAnnotation.allMatch());
    return transformToCondition(conditionAnnotation);
  }

  public static PropertyCondition transformToCondition(
      TemplateProperty.PropertyCondition conditionAnnotation) {

    if (!conditionAnnotation.equals().isBlank()) {
      return new PropertyCondition.Equals(
          conditionAnnotation.property(), conditionAnnotation.equals());
    } else if (conditionAnnotation.oneOf().length > 0) {
      return new PropertyCondition.OneOf(
          conditionAnnotation.property(), Arrays.asList(conditionAnnotation.oneOf()));
    } else if (conditionAnnotation.allMatch().length > 0) {
      return new PropertyCondition.AllMatch(
          Arrays.stream(conditionAnnotation.allMatch())
              .map(TemplatePropertyFieldProcessor::transformToNestedCondition)
              .toList());
    } else {
      // isActive always has a value, so we consider it is selected if nothing else is set
      return new PropertyCondition.IsActive(
          conditionAnnotation.property(), conditionAnnotation.isActive());
    }
  }

  public static PropertyCondition transformToNestedCondition(
      TemplateProperty.NestedPropertyCondition conditionAnnotation) {
    validateCondition(
        conditionAnnotation.property(),
        conditionAnnotation.equals(),
        conditionAnnotation.oneOf(),
        new NestedPropertyCondition[] {});
    if (!conditionAnnotation.equals().isBlank()) {
      return new PropertyCondition.Equals(
          conditionAnnotation.property(), conditionAnnotation.equals());
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
      String property, String equals, String[] oneOf, NestedPropertyCondition[] allMatch) {
    var equalsSet = !equals.isBlank();
    var oneOfSet = oneOf != null && oneOf.length > 0;
    var allMatchSet = allMatch != null && allMatch.length > 0;
    // equalsBoolean always has a value, so it's not included in the check
    // if everything else is not set, we consider it an equalsBoolean condition

    if (equalsSet && oneOfSet || equalsSet && allMatchSet || oneOfSet && allMatchSet) {
      throw new IllegalStateException(
          "Condition must have only one of 'equals', 'oneOf', 'isActive', or 'allMatch' set");
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

  private PropertyConstraints buildConstraints(TemplateProperty propertyAnnotation) {
    var constraintsAnnotation = propertyAnnotation.constraints();
    if (!constraintsAnnotation.notEmpty()
        && constraintsAnnotation.maxLength() == Integer.MAX_VALUE
        && constraintsAnnotation.minLength() == Integer.MIN_VALUE
        && constraintsAnnotation.pattern().value().isBlank()) {
      return null;
    }
    var builder = PropertyConstraints.builder();
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
      builder.pattern(
          new PropertyConstraints.Pattern(
              constraintsAnnotation.pattern().value(), constraintsAnnotation.pattern().message()));
    }
    return builder.build();
  }
}
