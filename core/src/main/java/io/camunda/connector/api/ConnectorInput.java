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
 * The input object containing the mapped variable input for further connector logic execution. This
 * is usually created in the very beginning of a connector function execution. It allows validation
 * and secrets replacement, also in nested {@link ConnectorInput}s. The input object can be used in
 * the {@link ConnectorContext} for various tasks like triggering validation and secrets
 * replacement.
 */
public interface ConnectorInput {

  /**
   * Validates the input's parameters with the given validator. You can easily validate nested input
   * objects using {@link #validateIfNotNull(ConnectorInput, Validator)}. By default, this method
   * does not validate anything for the input and can be left out if nothing needs validation.
   *
   * @param validator - providing validation methods and keeping track of requirements that were not
   *     met
   */
  default void validateWith(Validator validator) {
    // don't validate anything
  }

  /**
   * Replaces secrets in the input's parameters with the given secret store. You can easily replace
   * secrets in nested input objects using {@link #replaceSecretsIfNotNull(ConnectorInput,
   * SecretStore)}. By default, this method does not replace secrets anywhere for the input and can
   * be left out if nothing needs replacement.
   *
   * @param secretStore - providing secrets defined in the environment
   */
  default void replaceSecrets(SecretStore secretStore) {
    // don't replace any secrets
  }

  /**
   * Conveniently validates an input if it is not <code>null</code>. Usually used from within {@link
   * #validateWith(Validator)} for nested inputs.
   *
   * @param input - the input object to validate, if not <code>null</code>
   * @param validator - providing validation methods and keeping track of requirements that were not
   *     met
   */
  default void validateIfNotNull(ConnectorInput input, Validator validator) {
    if (input != null) {
      input.validateWith(validator);
    }
  }

  /**
   * Conveniently replaces secrets in an input if it is not <code>null</code>. Usually used from
   * within {@link #replaceSecrets(SecretStore)} for nested inputs.
   *
   * @param input - the input object to replace secrets in, if not <code>null</code>
   * @param secretStore - providing secrets defined in the environment
   */
  default void replaceSecretsIfNotNull(ConnectorInput input, SecretStore secretStore) {
    if (input != null) {
      input.replaceSecrets(secretStore);
    }
  }
}
