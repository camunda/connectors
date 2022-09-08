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

import io.camunda.connector.api.ConnectorContext;
import io.camunda.connector.api.ValidationProvider;
import io.camunda.connector.api.annotation.Secret;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;

public abstract class AbstractConnectorContext implements ConnectorContext {
  protected static final List<Class<?>> PRIMITIVE_TYPES =
      Arrays.asList(String.class, Number.class, Boolean.class);

  @Override
  public void replaceSecrets(Object input) {
    if (input == null) {
      return;
    }
    if (input.getClass().isArray()) {
      for (Object innerObject : (Object[]) input) {
        replaceSecrets(innerObject);
      }
    } else if (Iterable.class.isAssignableFrom(input.getClass())) {
      ((Iterable<?>) input).forEach(this::replaceSecrets);
    } else {
      Arrays.stream(input.getClass().getDeclaredFields())
          .filter(field -> field.isAnnotationPresent(Secret.class))
          .forEach(
              field -> {
                Object property = getProperty(input, field);
                if (hasNestedProperties(field, property)) {
                  replaceSecrets(property);
                } else if (CharSequence.class.isAssignableFrom(field.getType())) {
                  handleSecret(input, field);
                } else {
                  throw new IllegalStateException(
                      "Field '"
                          + field.getName()
                          + "' in type '"
                          + input.getClass()
                          + "'is marked as a secret, but it has no nested properties and is no string!");
                }
              });
    }
  }

  /**
   * Verify that the content of a field has nested properties that need to be checked for secrets
   *
   * @param field the field of the class the replacement is done for
   * @param property the content of the field
   * @return whether the given property in the given field potentially has inner properties
   */
  protected boolean hasNestedProperties(Field field, Object property) {
    Class<?> comparingClass = field.getType();
    if (property != null) {
      comparingClass = property.getClass();
    }
    for (Class<?> primitiveType : PRIMITIVE_TYPES) {
      if (primitiveType.isAssignableFrom(comparingClass)) {
        return false;
      }
    }
    return true;
  }

  protected <T> T getProperty(Object input, Field field) {
    if (field.canAccess(input)) {
      try {
        return (T) field.get(input);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
    try {
      Method getter =
          Arrays.stream(field.getDeclaringClass().getDeclaredMethods())
              // method has to be public
              .filter(method -> method.canAccess(input))
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

  protected void setProperty(Object input, Field field, Object property) {
    if (Modifier.isFinal(field.getModifiers())) {
      throw new IllegalStateException(
          "Cannot invoke set or setter on final field '"
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

  @Override
  public void validate(Object input) {
    getValidationProvider().validate(input);
  }

  /**
   * Override this method to provide your own {@link ValidationProvider} discovery strategy. By
   * default, SPI is being used and should be implemented by each implementation.
   *
   * @return the desired validation provider implementation
   */
  protected ValidationProvider getValidationProvider() {
    return ServiceLoader.load(ValidationProvider.class)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Please bind an implementation to "
                        + ValidationProvider.class.getName()
                        + " via SPI"));
  }

  private void handleSecret(Object secretsToReplace, Field fieldContainingSecret) {
    String secretName = getProperty(secretsToReplace, fieldContainingSecret);
    if (secretName != null) {
      String secretValue = getSecretStore().replaceSecret(secretName);
      setProperty(secretsToReplace, fieldContainingSecret, secretValue);
    }
  }
}
