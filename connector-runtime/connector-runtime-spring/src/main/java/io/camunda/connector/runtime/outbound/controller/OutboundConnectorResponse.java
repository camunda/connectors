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
package io.camunda.connector.runtime.outbound.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.camunda.connector.runtime.outbound.jobstream.BrokerConnectivityState;
import java.util.List;

/**
 * Represents a registered outbound connector on a specific runtime node.
 *
 * @param name connector name as declared in {@code @OutboundConnector}
 * @param type job type the worker subscribes to
 * @param inputVariables variables fetched from the job
 * @param timeout job timeout in milliseconds, or {@code null} if not configured
 * @param enabled whether the connector is enabled or not
 * @param runtimeId hostname of the runtime node that reported this entry
 * @param brokerConnectivityState whether the connector's stream appears as a consumer on all
 *     brokers of the physical tenant (engine) this entry belongs to; {@link
 *     BrokerConnectivityState#UNKNOWN} when broker monitoring is not configured or unreachable
 * @param streamIds consumer IDs observed across all brokers for this job type; {@code null} when
 *     broker monitoring is unavailable or no consumers were found
 * @param physicalTenantId the physical tenant (engine) this entry was reported for — the engine
 *     identity, as opposed to {@code runtimeId}, which is the reporting pod's identity. One entry
 *     per (connector, physical tenant) pair is returned, since every configured engine runs a job
 *     worker for every registered connector. {@code null} when no physical tenant is known (legacy
 *     single-client wiring that supplies no {@code CamundaClientRegistry}).
 */
@JsonInclude(Include.NON_NULL)
public record OutboundConnectorResponse(
    String name,
    String type,
    List<String> inputVariables,
    Long timeout,
    boolean enabled,
    String runtimeId,
    BrokerConnectivityState brokerConnectivityState,
    List<String> streamIds,
    String physicalTenantId) {

  /** Convenience constructor without stream enrichment (backward-compatible). */
  public OutboundConnectorResponse(
      String name,
      String type,
      List<String> inputVariables,
      Long timeout,
      boolean enabled,
      String runtimeId) {
    this(name, type, inputVariables, timeout, enabled, runtimeId, null, null, null);
  }

  /**
   * The canonical constructor as it stood before {@code physicalTenantId} was added — kept so
   * callers compiled against the previous signature keep linking, mirroring the stream-less
   * convenience constructor above.
   */
  public OutboundConnectorResponse(
      String name,
      String type,
      List<String> inputVariables,
      Long timeout,
      boolean enabled,
      String runtimeId,
      BrokerConnectivityState brokerConnectivityState,
      List<String> streamIds) {
    this(
        name,
        type,
        inputVariables,
        timeout,
        enabled,
        runtimeId,
        brokerConnectivityState,
        streamIds,
        null);
  }
}
