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
import io.camunda.connector.generator.dsl.PropertyBuilder;
import io.camunda.connector.generator.dsl.PropertyCondition;
import io.camunda.connector.generator.dsl.PropertyConstraints;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import java.lang.reflect.Field;
import java.util.Arrays;

/** {@link TemplateProperty} annotation processor */
public class TemplatePropertyFieldProcessor implements FieldProcessor {

  @Override
  public void process(Field field, PropertyBuilder builder) {
    var annotation = field.getAnnotation(TemplateProperty.class);
    if (annotation == null) {
      return;
    }
    builder.optional(annotation.optional());
    if (!(builder instanceof DropdownPropertyBuilder)) {
      builder.feel(annotation.feel());
    }
    if (!annotation.label().isBlank()) {
      builder.label(annotation.label());
    }
    if (!annotation.description().isBlank()) {
      builder.description(annotation.description());
    }
    if (!annotation.defaultValue().isBlank()) {
      builder.value(annotation.defaultValue());
    }
    if (!annotation.group().isBlank()) {
      builder.group(annotation.group());
    }
    builder.condition(buildCondition(annotation));
    builder.constraints(buildConstraints(annotation));
  }

  private PropertyCondition buildCondition(TemplateProperty propertyAnnotation) {
    var conditionAnnotation = propertyAnnotation.condition();
    if (conditionAnnotation.property().isBlank()) {
      return null;
    }
    if (conditionAnnotation.equals().isBlank() && conditionAnnotation.oneOf().length == 0) {
      throw new IllegalStateException("InvalidCondition must have either 'equals' or 'oneOf' set");
    }
    return transformToCondition(conditionAnnotation);
  }

  public static PropertyCondition transformToCondition(
      TemplateProperty.PropertyCondition conditionAnnotation) {

    if (!conditionAnnotation.equals().isBlank()) {
      return new PropertyCondition.Equals(
          conditionAnnotation.property(), conditionAnnotation.equals());
    } else {
      return new PropertyCondition.OneOf(
          conditionAnnotation.property(), Arrays.asList(conditionAnnotation.oneOf()));
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
