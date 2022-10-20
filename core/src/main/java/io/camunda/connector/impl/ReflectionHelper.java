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
package io.camunda.connector.impl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

/** Utility class for reflection helper methods, like finding getters and setter for fields. */
public class ReflectionHelper {

  private ReflectionHelper() {}

  /**
   * Gets the value of a field for an input object. Either, the field 'example' is accessible or the
   * object provides a getter that is named 'getExample' or 'isExample'.
   *
   * @param input the input object to get the property value from
   * @param field the field to get the value for
   * @return the field's value in the object
   * @param <T> the type of the field's value in the object
   */
  @SuppressWarnings("unchecked")
  public static <T> T getProperty(Object input, Field field) {
    final var inputToCall = Modifier.isStatic(field.getModifiers()) ? null : input;
    if (field.canAccess(inputToCall)) {
      try {
        return (T) field.get(inputToCall);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
    try {
      Method getter =
          Arrays.stream(field.getDeclaringClass().getDeclaredMethods())
              // method has to be public
              .filter(
                  method ->
                      method.canAccess(Modifier.isStatic(method.getModifiers()) ? null : input))
              // method has to follow java getter conventions
              .filter(
                  method ->
                      method
                              .getName()
                              .equals(
                                  "get"
                                      + field.getName().substring(0, 1).toUpperCase()
                                      + field.getName().substring(1))
                          || method
                              .getName()
                              .equals(
                                  "is"
                                      + field.getName().substring(0, 1).toUpperCase()
                                      + field.getName().substring(1)))
              // method must not have parameters
              .filter(method -> method.getParameterCount() == 0)
              .findFirst()
              .orElseThrow(
                  () ->
                      new NoSuchMethodException(
                          "no accessible getter found for field "
                              + field.getName()
                              + " of type "
                              + field.getDeclaringClass()));
      return (T) getter.invoke(input);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Sets the value of a field for an input object. Either, the field 'example' is accessible or the
   * object provides a getter that is named 'getExample' or 'isExample'.
   *
   * @param input the input object to set the property value at
   * @param field the field to set the value for
   * @param property the value to set for the field
   */
  public static void setProperty(Object input, Field field, Object property) {
    if (Modifier.isFinal(field.getModifiers())) {
      throw new IllegalStateException(
          "Cannot invoke set or setter on final field '"
              + field.getName()
              + "' of type "
              + field.getDeclaringClass());
    }
    if (Modifier.isStatic(field.getModifiers())) {
      throw new IllegalStateException(
          "Cannot invoke set or setter on static field '"
              + field.getName()
              + "' of type "
              + field.getDeclaringClass());
    }
    if (field.canAccess(input)) {
      try {
        field.set(input, property);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
    try {
      Method setter =
          Arrays.stream(field.getDeclaringClass().getDeclaredMethods())
              // method must not be static
              .filter(method -> !Modifier.isStatic(method.getModifiers()))
              // method has to be public
              .filter(method -> method.canAccess(input))
              // method has to follow java setter conventions
              .filter(
                  method ->
                      method
                          .getName()
                          .equals(
                              "set"
                                  + field.getName().substring(0, 1).toUpperCase()
                                  + field.getName().substring(1)))
              // method must have fitting parameter
              .filter(method -> method.getParameterCount() == 1)
              .filter(method -> method.getParameterTypes()[0].isAssignableFrom(property.getClass()))
              .findFirst()
              .orElseThrow(
                  () ->
                      new NoSuchMethodException(
                          "no accessible setter found for field "
                              + field.getName()
                              + " of type "
                              + field.getDeclaringClass()));
      setter.invoke(input, property);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
