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
import io.camunda.connector.runtime.outbound.jobstream.GatewayConnectivityState;
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
 * @param gatewayConnectivityState whether the gateway monitoring endpoint is reachable and the
 *     connector has a registered client stream; {@code null} when not configured
 * @param brokerConnectivityState how many brokers have the connector's stream as a consumer; {@code
 *     null} unless {@code gatewayConnectivityState} is {@code CONNECTED}
 * @param streamIds server-side stream IDs for the matched client streams; useful for debugging;
 *     {@code null} unless {@code gatewayConnectivityState} is {@code CONNECTED}
 */
@JsonInclude(Include.NON_NULL)
public record OutboundConnectorResponse(
    String name,
    String type,
    List<String> inputVariables,
    Long timeout,
    boolean enabled,
    String runtimeId,
    GatewayConnectivityState gatewayConnectivityState,
    BrokerConnectivityState brokerConnectivityState,
    List<String> streamIds) {

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
}
