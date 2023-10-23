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

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReflectionUtil {

  public static <T extends Annotation> T getRequiredAnnotation(
      Class<?> clazz, Class<T> annotationClass) {
    return getRequiredAnnotation(
        clazz,
        annotationClass,
        "Annotation " + annotationClass.getName() + " is required on " + clazz);
  }

  public static <T extends Annotation> T getRequiredAnnotation(
      Class<?> clazz, Class<T> annotationClass, String errorMessage) {
    var annotation = clazz.getAnnotation(annotationClass);
    if (annotation == null) {
      throw new IllegalStateException(errorMessage);
    }
    return annotation;
  }

  public static List<Field> getAllFields(Class<?> type) {
    return getAllFields(new ArrayList<>(), type);
  }

  public static List<Field> getAllFields(List<Field> fields, Class<?> type) {
    fields.addAll(Arrays.asList(type.getDeclaredFields()));

    if (type.getSuperclass() != null) {
      getAllFields(fields, type.getSuperclass());
    }
    return fields;
  }
}
