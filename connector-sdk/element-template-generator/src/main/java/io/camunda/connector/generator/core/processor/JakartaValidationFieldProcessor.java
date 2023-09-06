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
package io.camunda.connector.generator.core.processor;

import io.camunda.connector.generator.dsl.PropertyBuilder;
import io.camunda.connector.generator.dsl.PropertyConstraints;
import io.camunda.connector.generator.dsl.PropertyConstraints.PropertyConstraintsBuilder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.reflect.Field;
import org.apache.commons.lang3.tuple.Pair;

/** Jakarta Bean Validation API annotations processor */
public class JakartaValidationFieldProcessor implements FieldProcessor {

  @Override
  public void process(Field field, PropertyBuilder propertyBuilder) {
    PropertyConstraintsBuilder constraintsBuilder = PropertyConstraints.builder();

    if (hasNotEmptyConstraint(field)) {
      constraintsBuilder.notEmpty(true);
    }

    var minSize = hasMinSizeAnnotation(field);
    if (minSize != null) {
      constraintsBuilder.minLength(minSize);
    }
    var maxSize = hasMaxSizeAnnotation(field);
    if (maxSize != null) {
      constraintsBuilder.maxLength(maxSize);
    }

    var pattern = hasPatternAnnotation(field);
    if (pattern != null) {
      constraintsBuilder.pattern(
          new PropertyConstraints.Pattern(pattern.getLeft(), pattern.getRight()));
    }

    var constraints = constraintsBuilder.build();
    if (!isConstraintEmpty(constraints)) {
      propertyBuilder.constraints(constraints);
    }
  }

  private boolean hasNotEmptyConstraint(Field field) {
    return field.isAnnotationPresent(NotBlank.class)
        || field.isAnnotationPresent(NotEmpty.class)
        || field.isAnnotationPresent(NotNull.class);
  }

  private Integer hasMinSizeAnnotation(Field field) {
    var sizeAnnotation = field.getAnnotation(Size.class);
    if (sizeAnnotation != null && sizeAnnotation.min() != Integer.MIN_VALUE) {
      return sizeAnnotation.min();
    }
    return null;
  }

  private Integer hasMaxSizeAnnotation(Field field) {
    var sizeAnnotation = field.getAnnotation(Size.class);
    if (sizeAnnotation != null && sizeAnnotation.max() != Integer.MAX_VALUE) {
      return sizeAnnotation.max();
    }
    return null;
  }

  private Pair<String, String> hasPatternAnnotation(Field field) {
    var patternAnnotation = field.getAnnotation(Pattern.class);
    if (patternAnnotation != null) {
      return Pair.of(patternAnnotation.regexp(), patternAnnotation.message());
    }
    return null;
  }

  private boolean isConstraintEmpty(PropertyConstraints constraints) {
    return constraints.pattern() == null
        && constraints.minLength() == null
        && constraints.maxLength() == null
        && constraints.notEmpty() == null;
  }
}
