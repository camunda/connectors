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
package io.camunda.connector.runtime.inbound.executable;

import io.camunda.connector.runtime.core.inbound.ExecutableId;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable.Activated;
import java.util.List;
import java.util.function.Consumer;

public interface InboundExecutableRegistry {

  void publishEvent(InboundExecutableEvent event);

  /**
   * Query executables matching the given filter criteria.
   *
   * <p>Example usage: {@code registry.query(f -> f.bpmnProcessId("myProcess").tenantId("tenant1"))}
   *
   * @param filter consumer that configures the query filter
   * @return list of matching executable responses
   */
  List<ActiveExecutableResponse> query(Consumer<ActiveExecutableQuery> filter);

  String getConnectorName(String type);

  /**
   * Resets the executable with the given ID by deactivating it and re-activating it with a fresh
   * instance. Only executables in {@code Activated} or {@code Cancelled} state can be reset. Blocks
   * until the restart completes and returns the new {@link Activated} executable.
   *
   * @param id the ID of the executable to reset
   * @return the new {@link Activated} executable after the reset
   * @throws IllegalArgumentException if no executable with the given ID is found
   * @throws IllegalStateException if the executable is not in a resettable state
   * @throws RuntimeException if the restart fails
   */
  RegisteredExecutable reset(ExecutableId id);
}
