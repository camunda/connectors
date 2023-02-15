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
package io.camunda.connector.runtime.util;

import java.util.List;

/**
 * Connector factory stores all available Connector configurations and creates Connector instances
 *
 * @param <T> Connector supertype
 * @param <C> Connector configuration type
 */
public interface ConnectorFactory<T, C> {

  /**
   * List all available configurations loaded by the runtime
   *
   * @return List of available configurations
   */
  List<C> getConfigurations();

  /**
   * Create a Connector instance by type
   *
   * @param type Connector type
   * @return Connector instance
   */
  T getInstance(String type);

  /**
   * Dynamically register a new Connector configuration. If a connector with the same type already
   * exists, it will be overridden by the new configuration.
   *
   * @param configuration Configuration to register
   */
  void registerConfiguration(C configuration);

  /** Reload all connectors from classpath and reset all manually registered connectors */
  void resetConfigurations();
}
