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
 * Central function interface of a connector. This will be called from the environment-specific
 * runtime.
 */
public interface ConnectorFunction {

  /**
   * Executes the connector's central logic. The connector will receive a context object that allows
   * to fetch objects provided by the environment transparently.
   *
   * <p>The connector can return any serializable object that will be passed to the
   * environment-specific runtime.
   *
   * <p>Checked exceptions can be handled by the connector if desired. The environment-specifc
   * runtime will also take care of catching all checked exceptions from the connector function.
   *
   * @param context - allows to fetch information injected by the environment runtime
   * @return the result of the connector function, can be <code>null</code>
   * @throws Exception the connector function can potentially throw any checked exceptions
   */
  Object execute(ConnectorContext context) throws Exception;
}
