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
package io.camunda.connector.api;

/**
 * The context object provided to a connector function. The context allows to fetch information
 * injected by the environment runtime.
 */
public interface ConnectorContext {

  /**
   * @return the raw variables input as JSON String
   */
  String getVariables();

  /**
   * Maps the raw JSON variables String to the specified class. This is done in an
   * environment-specific way by the context of the related runtime. For primitive types and
   * supported structures like lists and maps, this is done automatically. For specific needs like
   * polymorphism in deserialization, please use {@link #getVariables()} and deserialize yourself.
   *
   * @param <T> - the type of the class to map the variables to
   * @param cls - the class representing the type to map the variables to
   * @return the mapped object containing the variable values
   */
  <T extends Object> T getVariablesAsType(Class<T> cls);

  /**
   * Fetches the secret store that is provided by the environment. You can use this to create your
   * own secret replacement routines. For default secret replacement mechanism, please use {@link
   * #replaceSecrets(ConnectorInput)}.
   *
   * @return the secret store provided by the environment
   */
  SecretStore getSecretStore();

  /**
   * Replaces the secrets in the input object by the defined secrets in the context's secret store.
   *
   * @param input - the object to replace secrets in
   */
  void replaceSecrets(ConnectorInput input);

  /**
   * Validates the input object regarding validation routines provided by the input object itself
   * via {@link ConnectorInput#validateWith(Validator)}.
   *
   * @param input - the object to validate
   */
  default void validate(ConnectorInput input) {
    final var validator = new Validator();
    input.validateWith(validator);
    validator.evaluate();
  }
}
