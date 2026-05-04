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
package io.camunda.connector.runtime.core;

import io.camunda.connector.runtime.core.common.AbstractConnectorFactory;
import io.camunda.connector.runtime.core.config.ConnectorConfiguration;
import java.util.Collection;

/**
 * Connector factory stores all available Connector configurations and creates Connector instances
 *
 * @param <T> Connector supertype
 * @param <C> Connector configuration type
 */
public interface ConnectorFactory<T, C extends ConnectorConfiguration> {

  /**
   * List all active connector configurations loaded by the runtime. A configuration is considered
   * active when it has not been disabled via the {@code CONNECTOR_<NAME>_DISABLED} environment
   * variable.
   *
   * @return list of active {@link ConnectorConfiguration} entries; never {@code null}
   * @see #getRuntimeConfigurations()
   */
  Collection<C> getActiveConfigurations();

  /**
   * List all connector configurations loaded by the runtime, including those that have been
   * disabled via the {@code CONNECTOR_<NAME>_DISABLED} environment variable.
   *
   * @return list of all {@link
   *     io.camunda.connector.runtime.core.common.AbstractConnectorFactory.ConnectorRuntimeConfiguration}
   *     entries; never {@code null}
   */
  Collection<AbstractConnectorFactory.ConnectorRuntimeConfiguration<C>> getRuntimeConfigurations();

  /**
   * Create a Connector instance by type
   *
   * @param type Connector type
   * @return Connector instance
   */
  T getInstance(String type);
}
