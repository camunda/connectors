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
import io.camunda.connector.runtime.inbound.state.model.ProcessDefinitionRef;
import io.camunda.connector.runtime.inbound.state.model.StateUpdateResult;
import java.util.Set;

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

  /**
   * Returns the process definition keys (versions) currently active for the given process, without
   * modifying any state.
   *
   * <p>{@link #compareAndUpdate} only reports a given state transition once, so a caller that
   * failed to act on one cannot obtain it again from a later import. This lets such a caller
   * re-read the current state instead, so its retry acts on what is active now rather than on a
   * remembered — and possibly superseded — set of versions.
   *
   * @param processRef the process definition to look up
   * @return the active process definition keys, or an empty set if the process is not tracked
   */
  Set<Long> getActiveVersions(ProcessDefinitionRef processRef);
}
