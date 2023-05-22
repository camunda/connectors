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

import io.camunda.connector.impl.inbound.InboundConnectorProperties;
import io.camunda.connector.impl.inbound.ProcessCorrelationPoint;
import io.camunda.connector.impl.inbound.result.MessageCorrelationResult;
import io.camunda.connector.impl.inbound.result.StartEventCorrelationResult;

/**
 * The context object provided to an inbound connector function. The context allows to fetch
 * information injected by the environment runtime.
 */
public interface InboundConnectorContext {

  /**
   * Replaces the secrets in the input object by the defined secrets in the context's secret store.
   *
   * @param input - the object to replace secrets in
   */
  void replaceSecrets(Object input);

  /**
   * Validates the input object
   *
   * @param input - the object to validate
   */
  void validate(Object input);

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
   * Low-level properties access method. Allows to perform custom deserialization, or access
   * internal properties of the process correlation point.
   *
   * <p>For a simpler property access, consider using {@link #getPropertiesAsType(Class)}
   *
   * @return - raw properties as an {@link InboundConnectorProperties} object
   */
  InboundConnectorProperties getProperties();

  /**
   * High-level properties access method. Deserializes inbound Connector properties to the requested
   * type. Deserialization logic is runtime-specific. If you need a lower-level access to properties
   * (e.g. for custom deserialization), use {@link #getProperties()}
   *
   * @return - Connector-specific properties deserialized to a provided type
   */
  <T> T getPropertiesAsType(Class<T> cls);

  /**
   * Report the health to allow other components to process the current status of the Connector. The
   * data can be used to report data on connectiveness, liveliness and whether the Connector is
   * running successfully.
   *
   * <p>This method can be called as often as needed and the internal state of the inbound Connector
   * implementation requires it.
   *
   * @param @{@link Health} health of the inbound connector including optional details about the
   *     status.
   */
  void reportHealth(Health health);
}
