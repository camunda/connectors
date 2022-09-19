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
package io.camunda.connector.impl.outbound;

import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.validation.ValidationProvider;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Consumer;

public abstract class AbstractOutboundConnectorContext implements OutboundConnectorContext {

  protected static final List<Class<?>> PRIMITIVE_TYPES =
      List.of(String.class, Number.class, Boolean.class);

  @Override
  public void validate(Object input) {
    getValidationProvider().validate(input);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void replaceSecrets(Object input) {
    if (input == null) {
      return;
    }
    if (input.getClass().isArray()) {
      handleSecretsArray((Object[]) input);
    } else if (input instanceof Map) {
      handleSecretsMap((Map<Object, Object>) input);
    } else if (input instanceof List) {
      handleSecretsList((List<Object>) input);
    } else if (input instanceof Iterable) {
      handleSecretsIterable((Iterable<?>) input);
    } else {
      handleSecretsField(input);
    }
  }

  protected void handleSecretsArray(Object[] input) {
    final var array = input;
    for (int i = 0; i < array.length; i++) {
      int index = i;
      handleSecretsElement(
          array[index],
          "Element at index " + index + " in array has no nested properties and is no String!",
          "Array",
          value -> array[index] = value);
    }
  }

  protected void handleSecretsMap(final Map<Object, Object> input) {
    input.forEach(
        (k, v) ->
            handleSecretsElement(
                v,
                "Element at key '" + k + "' in map has no nested properties and is no String!",
                "Map",
                value -> input.put(k, value)));
  }

  protected void handleSecretsList(List<Object> input) {
    for (ListIterator<Object> iterator = input.listIterator(); iterator.hasNext(); ) {
      handleSecretsElement(
          iterator.next(),
          "Element at index "
              + iterator.previousIndex()
              + " in list has no nested properties and is no String!",
          "List",
          iterator::set);
    }
  }

  protected void handleSecretsIterable(Iterable<?> input) {
    for (Object o : input) {
      handleSecretsElement(o, "Element in iterable has no nested properties!", "Set", null);
    }
  }

  protected void handleSecretsField(Object input) {
    Arrays.stream(input.getClass().getDeclaredFields())
        .filter(field -> field.isAnnotationPresent(Secret.class))
        .forEach(
            field ->
                handleSecretsElement(
                    getProperty(input, field),
                    "Field '"
                        + field.getName()
                        + "' in type '"
                        + input.getClass()
                        + "'is marked as a secret, but it has no nested properties and is no String!",
                    "Field",
                    value -> setProperty(input, field, value)));
  }

  protected void handleSecretsElement(
      Object value, String failureMessage, String type, Consumer<String> setValueHandler) {
    Optional.ofNullable(value)
        .ifPresent(
            element -> {
              if (isSecretContainer(element)) {
                replaceSecrets(element);
              } else if (setValueHandler != null && element instanceof String) {
                try {
                  setValueHandler.accept(getSecretStore().replaceSecret((String) element));
                } catch (UnsupportedOperationException uoe) {
                  throw new IllegalStateException(
                      type + " is immutable but contains String secrets to replace!");
                }
              } else {
                throw new IllegalStateException(failureMessage);
              }
            });
  }

  protected static boolean isSecretContainer(Object property) {
    return Optional.ofNullable(property.getClass()).filter(c -> !isPrimitive(c)).isPresent();
  }

  protected static boolean isPrimitive(Class<?> clazz) {
    return clazz.isPrimitive() || PRIMITIVE_TYPES.stream().anyMatch(c -> c.isAssignableFrom(clazz));
  }

  @SuppressWarnings("unchecked")
  protected static <T> T getProperty(Object input, Field field) {
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

  protected static void setProperty(Object input, Field field, Object property) {
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
}
