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
package io.camunda.connector.impl.secret;

import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.api.secret.SecretContainerHandler;
import io.camunda.connector.api.secret.SecretElementHandler;
import io.camunda.connector.api.secret.SecretStore;
import io.camunda.connector.impl.ReflectionHelper;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Internal default implementation for a {@link SecretElementHandler} and {@link
 * SecretContainerHandler} in one class. This handler provides default behavior for common Java
 * container classes like Lists, Maps, Arrays, and Iterables. It also defines a default strategy for
 * all other container objects, like custom classes with fields marked with the @{@link Secret}
 * annotation.
 */
public class SecretHandler implements SecretElementHandler, SecretContainerHandler {

  protected static final List<Class<?>> PRIMITIVE_TYPES =
      List.of(String.class, Number.class, Boolean.class);

  private final SecretStore secretStore;

  public SecretHandler(final SecretStore secretStore) {
    this.secretStore = secretStore;
  }

  @Override
  public void handleSecretElement(
      Object value, String failureMessage, String type, Consumer<String> setValueHandler) {
    handleSecretElement(value, failureMessage, type, setValueHandler, this);
  }

  @Override
  public void handleSecretContainer(Object input, SecretElementHandler elementHandler) {
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
      handleSecretElement(
          array[index],
          "Element at index " + index + " in array has no nested properties and is no String!",
          "Array",
          value -> array[index] = value);
    }
  }

  protected void handleSecretsMap(final Map<Object, Object> input) {
    input.forEach(
        (k, v) ->
            handleSecretElement(
                v,
                "Element at key '" + k + "' in map has no nested properties and is no String!",
                "Map",
                value -> input.put(k, value)));
  }

  protected void handleSecretsList(List<Object> input) {
    for (ListIterator<Object> iterator = input.listIterator(); iterator.hasNext(); ) {
      handleSecretElement(
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
      handleSecretElement(o, "Element in iterable has no nested properties!", "Set", null);
    }
  }

  protected void handleSecretsField(Object input) {
    Arrays.stream(input.getClass().getDeclaredFields())
        .filter(field -> field.isAnnotationPresent(Secret.class))
        .forEach(
            field -> {
              handleSecretElement(
                  ReflectionHelper.getProperty(input, field),
                  "Field '"
                      + field.getName()
                      + "' in type '"
                      + input.getClass()
                      + "'is marked as a secret, but it has no nested properties and is no String!",
                  "Field",
                  value -> ReflectionHelper.setProperty(input, field, value),
                  getSecretContainerHandler(field));
            });
  }

  protected SecretContainerHandler getSecretContainerHandler(Field field) {
    final var containerHandler = field.getAnnotation(Secret.class).handler();
    try {
      return containerHandler.equals(getClass())
          ? this
          : containerHandler.getConstructor().newInstance();
    } catch (Exception e) {
      throw new IllegalStateException(
          "Cannot instantiate secret container handler " + containerHandler, e);
    }
  }

  protected void handleSecretElement(
      Object value,
      String failureMessage,
      String type,
      Consumer<String> setValueHandler,
      SecretContainerHandler containerHandler) {
    Optional.ofNullable(value)
        .ifPresent(
            element -> {
              if (isSecretContainer(element)) {
                containerHandler.handleSecretContainer(element, this);
              } else if (setValueHandler != null && element instanceof String) {
                try {
                  setValueHandler.accept(secretStore.replaceSecret((String) element));
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
}
