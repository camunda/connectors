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
package io.camunda.connector.generator.core.util;

import io.camunda.connector.generator.annotation.TemplateProperty;
import io.camunda.connector.generator.dsl.DropdownProperty.DropdownPropertyBuilder;
import io.camunda.connector.generator.dsl.Property.FeelMode;
import io.camunda.connector.generator.dsl.PropertyBuilder;
import io.camunda.connector.generator.dsl.PropertyCondition;
import java.util.Arrays;

public class TemplatePropertyAnnotationUtil {

  public static PropertyBuilder applyAnnotation(
      PropertyBuilder builder, TemplateProperty annotation) {
    if (annotation == null) {
      return builder;
    }
    builder.optional(annotation.optional());
    if (annotation.feel() != FeelMode.disabled && !(builder instanceof DropdownPropertyBuilder)) {
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
    return builder;
  }

  private static PropertyCondition buildCondition(TemplateProperty propertyAnnotation) {
    var conditionAnnotation = propertyAnnotation.condition();
    if (conditionAnnotation.property().isBlank()) {
      return null;
    }
    if (conditionAnnotation.equals().isBlank() && conditionAnnotation.oneOf().length == 0) {
      throw new IllegalStateException("InvalidCondition must have either 'equals' or 'oneOf' set");
    }
    if (!conditionAnnotation.equals().isBlank()) {
      return new PropertyCondition.Equals(
          conditionAnnotation.property(), conditionAnnotation.equals());
    } else {
      return new PropertyCondition.OneOf(
          conditionAnnotation.property(), Arrays.asList(conditionAnnotation.oneOf()));
    }
  }
}
