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
package io.camunda.connector.runtime.inbound.state;

import io.camunda.connector.runtime.inbound.state.model.ImportResult;

/**
 * This component is responsible for coordinating the update of the internal state of the inbound
 * connector runtime based on the imported data.
 *
 * <p>Internally, it uses a {@link ProcessStateContainer} to compare the current state with the
 * newly imported data and determine which process definitions need to be activated or deactivated.
 */
public interface ProcessStateManager {

  /**
   * 1. Compares the current state with the newly imported data and determines which process
   * definitions need to be activated or deactivated.
   *
   * <p>2. For each process definition that needs to be activated or deactivated, retrieves the
   * relevant inbound connector elements using a {@link ProcessDefinitionInspector}.
   *
   * <p>3. Publishes the corresponding events to the {@link
   * io.camunda.connector.runtime.inbound.executable.InboundExecutableRegistry}.
   *
   * <p>This method has side effect of updating the current state and activating or deactivating the
   * necessary inbound connectors.
   *
   * @param processDefinitions all imported process definitions for this import type (including the
   *     ones that are not changed)
   */
  void update(ImportResult processDefinitions);
}
