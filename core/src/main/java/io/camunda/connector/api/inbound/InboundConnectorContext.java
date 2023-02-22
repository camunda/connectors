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

  /** Correlates the inbound event to the matching process definition */
  InboundConnectorResult correlate(Object variables);

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
}
