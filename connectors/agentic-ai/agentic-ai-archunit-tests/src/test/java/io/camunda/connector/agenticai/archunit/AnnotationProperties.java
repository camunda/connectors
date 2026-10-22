/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.archunit;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import java.util.ArrayList;
import java.util.List;

/** Reads the explicitly declared attributes of an annotation without loading its class. */
final class AnnotationProperties {

  private AnnotationProperties() {}

  /** The string values of an attribute, empty when the attribute is not declared. */
  static List<String> strings(final JavaAnnotation<?> annotation, final String attribute) {
    final List<String> values = new ArrayList<>();
    annotation
        .tryGetExplicitlyDeclaredProperty(attribute)
        .ifPresent(
            value -> {
              if (value instanceof final Object[] array) {
                for (final Object element : array) {
                  values.add(String.valueOf(element));
                }
              } else {
                values.add(String.valueOf(value));
              }
            });
    return values;
  }

  /** The value of a boolean attribute, {@code false} when the attribute is not declared. */
  static boolean bool(final JavaAnnotation<?> annotation, final String attribute) {
    return annotation
        .tryGetExplicitlyDeclaredProperty(attribute)
        .map(Boolean.TRUE::equals)
        .orElse(false);
  }
}
