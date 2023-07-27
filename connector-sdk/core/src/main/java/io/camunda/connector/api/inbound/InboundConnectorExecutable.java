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
 * Base interface for inbound connectors. The environment-specific Connector Runtime uses this
 * interface to control the execution inbound Connectors.
 */
public interface InboundConnectorExecutable {

  /**
   * Activation trigger for the inbound Connector executable.
   *
   * <p>Implementation <b>must be asynchronous</b> and must not block the calling thread. Therefore,
   * any blocking calls triggered within this method must happen in a separate thread instantiated
   * in this method.
   *
   * <p>Once activated, the inbound Connector execution is considered active and running. From this
   * point, it should use the respective methods of {@link InboundConnectorContext} to communicate
   * with the Connector runtime (e.g. to correlate the inbound event or signal the interrupt).
   *
   * @param context Runtime-specific information
   */
  void activate(InboundConnectorContext context) throws Exception;

  /**
   * Gentle shutdown hook for inbound connectors. Must release all resources used by the
   * subscription.
   */
  void deactivate() throws Exception;
}
