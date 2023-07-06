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

import io.camunda.connector.impl.inbound.ProcessCorrelationPoint;
import io.camunda.connector.impl.inbound.result.MessageCorrelationResult;
import io.camunda.connector.impl.inbound.result.StartEventCorrelationResult;
import java.util.Map;

/**
 * The context object provided to an inbound connector function. The context allows to fetch
 * information injected by the environment runtime.
 */
public interface InboundConnectorContext {

  /**
   * Correlates the inbound event to the matching process definition
   *
   * <p>Correlation may not succeed due to Connector configuration (e.g. if activation condition
   * specified by user is not met). In this case, the response will contain error details.
   *
   * <p>In case of an unexpected runtime error, an unchecked {@link
   * io.camunda.connector.api.error.ConnectorException} will be thrown.
   *
   * @param variables - an object containing inbound connector variables
   * @return either {@link MessageCorrelationResult} or {@link StartEventCorrelationResult},
   *     depending on the type of the underlying {@link ProcessCorrelationPoint}.
   * @throws io.camunda.connector.impl.ConnectorInputException if the correlation fails due to
   *     invalid input. In this case, correlation should not be retried.
   * @throws io.camunda.connector.api.error.ConnectorException if the correlation fails due to
   *     unexpected runtime error. Such errors may be temporary and can be retried.
   */
  InboundConnectorResult<?> correlate(Object variables);

  /**
   * Signals to the Connector runtime that inbound Connector execution was interrupted. As a result
   * of this call, the runtime may attempt to retry the execution or provide the user with an
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
   * <p>FEEL expressions are evaluated for all fields which are not listed in the {@link
   * io.camunda.connector.impl.Constants#RESERVED_KEYWORDS}.
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
}
