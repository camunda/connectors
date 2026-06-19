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
package io.camunda.connector.runtime.instances.service;

import io.camunda.connector.runtime.core.common.AbstractConnectorFactory;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;
import io.camunda.connector.runtime.inbound.controller.exception.DataNotFoundException;
import io.camunda.connector.runtime.outbound.controller.OutboundConnectorResponse;
import io.camunda.connector.runtime.outbound.jobstream.BrokerJobStreamClient;
import io.camunda.connector.runtime.outbound.jobstream.GatewayJobStreamClient;
import io.camunda.connector.runtime.outbound.jobstream.GatewayResult;
import io.camunda.connector.runtime.outbound.jobstream.RemoteJobStream;
import io.camunda.connector.runtime.outbound.jobstream.StreamConnectivity;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutboundConnectorsService {

  private static final Logger LOG = LoggerFactory.getLogger(OutboundConnectorsService.class);

  private final OutboundConnectorFactory connectorFactory;
  private final GatewayJobStreamClient gatewayJobStreamClient;
  private final BrokerJobStreamClient brokerJobStreamClient;

  public OutboundConnectorsService(OutboundConnectorFactory connectorFactory) {
    this(connectorFactory, null, null);
  }

  public OutboundConnectorsService(
      OutboundConnectorFactory connectorFactory, GatewayJobStreamClient gatewayJobStreamClient) {
    this(connectorFactory, gatewayJobStreamClient, null);
  }

  public OutboundConnectorsService(
      OutboundConnectorFactory connectorFactory,
      GatewayJobStreamClient gatewayJobStreamClient,
      BrokerJobStreamClient brokerJobStreamClient) {
    this.connectorFactory = connectorFactory;
    this.gatewayJobStreamClient = gatewayJobStreamClient;
    this.brokerJobStreamClient = brokerJobStreamClient;
  }

  public List<OutboundConnectorResponse> findAll(String runtimeId) {
    GatewayResult gateway = queryGateway();
    Optional<List<RemoteJobStream>> remoteStreams = resolveBrokerStreams(gateway);
    return connectorFactory.getRuntimeConfigurations().stream()
        .map(config -> toResponse(config, runtimeId, gateway, remoteStreams))
        .toList();
  }

  public List<OutboundConnectorResponse> findByType(String type, String runtimeId) {
    GatewayResult gateway = queryGateway();
    Optional<List<RemoteJobStream>> remoteStreams = resolveBrokerStreams(gateway);
    var results =
        connectorFactory.getRuntimeConfigurations().stream()
            .filter(config -> config.config().type().equals(type))
            .map(config -> toResponse(config, runtimeId, gateway, remoteStreams))
            .toList();
    if (results.isEmpty()) {
      throw new DataNotFoundException(OutboundConnectorResponse.class, type);
    }
    return results;
  }

  private GatewayResult queryGateway() {
    if (gatewayJobStreamClient == null) {
      return new GatewayResult.Failure.Unknown();
    }
    try {
      return new GatewayResult.Success(gatewayJobStreamClient.fetchJobStreams());
    } catch (Exception e) {
      LOG.warn("Failed to fetch job streams from gateway: {}", e.getMessage());
      return new GatewayResult.Failure.Unreachable();
    }
  }

  /**
   * Resolves the broker-side remote streams to use for broker connectivity state computation.
   *
   * <ul>
   *   <li>If broker monitoring addresses are configured, query brokers directly.
   *   <li>Otherwise, fall back to the gateway's own {@code remote} data (non-empty only when the
   *       gateway is embedded in a broker).
   *   <li>Returns {@link Optional#empty()} when broker state cannot be determined (standalone
   *       gateway with no broker addresses configured, or gateway unreachable).
   * </ul>
   */
  private Optional<List<RemoteJobStream>> resolveBrokerStreams(GatewayResult gateway) {
    if (!(gateway instanceof GatewayResult.Success success)) {
      return Optional.empty();
    }
    if (brokerJobStreamClient != null) {
      try {
        return Optional.of(brokerJobStreamClient.fetchRemoteStreams());
      } catch (Exception e) {
        LOG.warn("Failed to fetch remote streams from brokers: {}", e.getMessage());
        return Optional.empty();
      }
    }
    // Fallback: use gateway's remote field (populated only for embedded gateways).
    // Empty remote in a standalone gateway means we cannot determine broker state.
    List<RemoteJobStream> gatewayRemote = success.streams().remote();
    return gatewayRemote.isEmpty() ? Optional.empty() : Optional.of(gatewayRemote);
  }

  private OutboundConnectorResponse toResponse(
      AbstractConnectorFactory.ConnectorRuntimeConfiguration<OutboundConnectorConfiguration> config,
      String runtimeId,
      GatewayResult gateway,
      Optional<List<RemoteJobStream>> remoteStreams) {
    String jobType = config.config().type();
    StreamConnectivity connectivity =
        switch (gateway) {
          case GatewayResult.Failure f -> StreamConnectivity.unavailable(f.gatewayState());
          case GatewayResult.Success s ->
              StreamConnectivity.compute(jobType, s.streams().client(), remoteStreams);
        };
    return buildResponse(config, runtimeId, connectivity);
  }

  private OutboundConnectorResponse buildResponse(
      AbstractConnectorFactory.ConnectorRuntimeConfiguration<OutboundConnectorConfiguration> config,
      String runtimeId,
      StreamConnectivity connectivity) {
    var connectorConfig = config.config();
    return new OutboundConnectorResponse(
        connectorConfig.name(),
        connectorConfig.type(),
        connectorConfig.inputVariables() == null
            ? List.of()
            : List.of(connectorConfig.inputVariables()),
        connectorConfig.timeout(),
        config.isActive(),
        runtimeId,
        connectivity.gatewayState(),
        connectivity.brokerState(),
        connectivity.streamIds());
  }
}
