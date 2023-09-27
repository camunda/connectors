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
package io.camunda.connector.api.inbound;

/**
 * Represents the context of a specific process instance. This interface defines methods to interact
 * with, modify, and retrieve information related to a unique instance of a process.
 */
public interface ProcessInstanceContext {

  /**
   * Retrieves the key associated with this process instance.
   *
   * @return The unique identifier for this process instance.
   */
  Long getKey();

  /**
   * Binds the stored properties to a specified class type. It facilitates the deserialization and
   * conversion of these properties into a desired data structure or object. In cases where binding
   * the properties to the specified class isn't possible, this method will throw a runtime
   * exception.
   *
   * @param <T> The target type for deserialization of the properties.
   * @param cls The class type blueprint for the deserialization process.
   * @return An instance of the desired type {@code T} containing the deserialized and bound
   *     properties.
   * @throws RuntimeException if the evaluation of any property fails or if validation fails.
   */
  <T> T bind(Class<T> cls);

  /**
   * Correlates the inbound event to the matching process definition.
   *
   * <p>Correlation may not succeed due to Connector configuration (e.g. if the activation condition
   * specified by the user is not met). In this case, the response will contain error details.
   *
   * <p>In case of an unexpected runtime error, an unchecked {@link
   * io.camunda.connector.api.error.ConnectorException} will be thrown.
   *
   * @param variables An object containing inbound connector variables.
   * @throws io.camunda.connector.api.error.ConnectorInputException if the correlation fails due to
   *     invalid input. In this case, correlation should not be retried.
   * @throws io.camunda.connector.api.error.ConnectorException if the correlation fails due to an
   *     unexpected runtime error. Such errors may be temporary and can be retried.
   */
  void correlate(Object variables);
}
