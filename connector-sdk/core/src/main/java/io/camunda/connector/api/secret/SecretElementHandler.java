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
package io.camunda.connector.api.secret;

import java.util.function.Consumer;

/**
 * Handler for secret elements. This allows to define a strategy for handling single elements in a
 * secret container, e.g. differentiating between nested container objects and primitive types.
 *
 * <p><b>Deprecated:</b> Secret support is now available all fields during the binding process
 * without the need to annotate individual fields.
 */
@Deprecated(forRemoval = true)
public interface SecretElementHandler {

  /**
   * Handles the element of a secret container. If a primitive type that can hold a secret and can
   * be replaced is found, the <code>setValueHandler</code> is called.
   *
   * @param value the secret container element to handle
   * @param failureMessage message to throw if the element cannot be handled
   * @param type the type of the container the element is in; can be used for appropriate error
   *     messaging if a set operation is not supported by the container
   * @param setValueHandler the String consumer of the new secret value that alters the value in the
   *     container in case it is a String element
   */
  void handleSecretElement(
      Object value, String failureMessage, String type, Consumer<String> setValueHandler);
}
