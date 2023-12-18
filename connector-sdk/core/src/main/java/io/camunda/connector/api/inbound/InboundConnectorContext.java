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

import java.util.Map;

/**
 * The context object provided to an inbound connector function. The context allows to fetch
 * information injected by the environment runtime.
 */
public interface InboundConnectorContext {

  /**
   * Correlates the inbound event to the matching process definition
   *
   * @deprecated since 8.4. Use {@link #correlateWithResult(Object)} instead.
   */
  @Deprecated(since = "8.4")
  void correlate(Object variables);

  /**
   * Correlates the inbound event to the matching process definition and returns the result.
   *
   * <p>Correlation may not succeed due to Connector configuration (e.g. if activation condition
   * specified by user is not met). In this case, the response will contain the corresponding error
   * code.
   *
   * <p>This method does not throw any exceptions. If correlation fails, the error is returned as a
   * part of the response.
   *
   * @param variables - an object containing inbound connector variables
   */
  CorrelationResult correlateWithResult(Object variables);

  /**
   * /** Signals to the Connector runtime that inbound Connector execution was interrupted. As a
   * result of this call, the runtime may attempt to retry the execution or provide the user with an
   * appropriate alert.
   */
  void cancel(Throwable exception);

  /**
   * Low-level properties access method. Allows to perform custom deserialization. For a simpler
   * property access, consider using {@link #bindProperties(Class)} (Class)}.
   *
   * <p>Note: this method doesn't perform validation or FEEl expression evaluation. Secret
   * replacement is performed using the {@link io.camunda.connector.api.secret.SecretProvider}
   * implementation available in the Connector runtime.
   *
   * @return raw properties as a map with secrets replaced
   */
  Map<String, Object> getProperties();

  /**
   * High-level properties access method. Allows to deserialize properties into a given type.
   *
   * <p>Additionally, this method takes care of secret replacement, properties validation, and FEEL
   * expression evaluation.
   *
   * <p>Secret values are substituted using the {@link
   * io.camunda.connector.api.secret.SecretProvider} implementations available in the Connector
   * runtime.
   *
   * <p>Properties validation is performed using the {@link
   * io.camunda.connector.api.validation.ValidationProvider} implementation available in the
   * Connector runtime.
   *
   * <p>FEEL expressions in properties are evaluated as encountered.
   *
   * @param cls a class to deserialize properties into
   * @param <T> a type to deserialize properties into
   * @return deserialized and validated properties with secrets replaced
   */
  <T> T bindProperties(Class<T> cls);

  /**
   * Provides an object that references the process definition that the inbound Connector is
   * configured for. The object can be used to access the process definition metadata.
   *
   * @return definition of the inbound Connector
   */
  InboundConnectorDefinition getDefinition();

  /**
   * Report the health to allow other components to process the current status of the Connector. The
   * data can be used to report data on liveliness and whether the Connector is running
   * successfully.
   *
   * <p>This method can be called as often as needed and the internal state of the inbound Connector
   * implementation requires it.
   */
  void reportHealth(Health health);

  /**
   * Provides a Health object to get information about the current status of the Connector with
   * optional details.
   *
   * <p>Use the {@link #reportHealth(Health)} method to set this information
   *
   * @return Health object
   */
  Health getHealth();
}
