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
import io.camunda.connector.runtime.inbound.state.model.StateUpdateResult;

/**
 * Container for the current process state. It is responsible for comparing the current state with
 * the newly imported data and determining which process definitions need to be activated or
 * deactivated.
 */
public interface ProcessStateContainer {

  /**
   * Compare the current state with the newly imported data and determine which process definitions
   * need to be activated or deactivated. This method has a side effect of updating the current
   * state to reflect the newly imported data.
   *
   * @param importResult all imported process definitions for this import type (including the ones
   *     that are not changed)
   * @return the result of the state update: which process definitions must be activated or
   *     deactivated
   */
  StateUpdateResult compareAndUpdate(ImportResult importResult);
}
