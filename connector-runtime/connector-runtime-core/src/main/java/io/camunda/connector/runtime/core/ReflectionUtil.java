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
package io.camunda.connector.runtime.core;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utility class for reflection operations, specifically for retrieving methods annotated with a
 * specific annotation. TODO Move to a reusable package so it can be shared with the element
 * template generator
 */
public class ReflectionUtil {

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
}
