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
package io.camunda.connector.api.reflection;

import io.camunda.connector.api.annotation.Operation;
import io.camunda.connector.api.annotation.Variable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utility class for reflection operations related to annotations and methods. Provides methods to
 * retrieve annotations, fields, and methods with specific annotations.
 *
 * <p>This class is only intended for internal use within the connector SDK and should not be used
 * by client code.
 */
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

  public record MethodWithAnnotation<A extends Annotation>(
      Method method, List<Parameter> parameters, A annotation) {}

  public static <A extends Annotation> List<MethodWithAnnotation<A>> getMethodsAnnotatedWith(
      final Class<?> type, final Class<? extends Annotation> annotation) {
    final List<MethodWithAnnotation<A>> methods = new ArrayList<>();
    Class<?> klass = type;
    while (klass
        != Object
            .class) { // need to traverse a type hierarchy in order to process methods from super
      // types
      // iterate though the list of methods declared in the class represented by klass variable, and
      // add those annotated with the specified annotation
      for (final Method method : klass.getDeclaredMethods()) {
        if (method.isAnnotationPresent(annotation)) {
          Annotation methodAnnotation = method.getAnnotation(annotation);
          method.setAccessible(true);
          methods.add(
              new MethodWithAnnotation(
                  method, Arrays.asList(method.getParameters()), methodAnnotation));
        }
      }
      // move to the upper class in the hierarchy in search for more methods
      klass = klass.getSuperclass();
    }
    return methods;
  }

  public static String getOperationName(Operation operation) {
    return !operation.name().isBlank() ? operation.name() : getOperationId(operation);
  }

  public static String getOperationId(Operation operation) {
    if (!operation.id().isBlank()) {
      return operation.id();
    } else if (!operation.value().isBlank()) {
      return operation.value();
    } else {
      throw new IllegalStateException(
          "Operation must have either 'id' or 'value' set. Operation: " + operation);
    }
  }

  public static String getVariableName(Variable variable) {
    if (variable.name().isBlank()) {
      return variable.name();
    } else if (!variable.value().isBlank()) {
      return variable.value();
    } else {
      throw new IllegalStateException(
          "Variable must have either 'name' or 'value' set. Variable: " + variable);
    }
  }
}
