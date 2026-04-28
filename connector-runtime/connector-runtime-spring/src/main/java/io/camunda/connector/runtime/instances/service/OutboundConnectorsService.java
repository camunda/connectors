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
import io.camunda.connector.runtime.outbound.jobstream.GatewayJobStreamClient;
import io.camunda.connector.runtime.outbound.jobstream.GatewayResult;
import io.camunda.connector.runtime.outbound.jobstream.StreamConnectivity;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutboundConnectorsService {

  private static final Logger LOG = LoggerFactory.getLogger(OutboundConnectorsService.class);

  private final OutboundConnectorFactory connectorFactory;
  private final GatewayJobStreamClient gatewayJobStreamClient;

  public OutboundConnectorsService(OutboundConnectorFactory connectorFactory) {
    this(connectorFactory, null);
  }

  public OutboundConnectorsService(
      OutboundConnectorFactory connectorFactory, GatewayJobStreamClient gatewayJobStreamClient) {
    this.connectorFactory = connectorFactory;
    this.gatewayJobStreamClient = gatewayJobStreamClient;
  }

  public List<OutboundConnectorResponse> findAll(String runtimeId) {
    GatewayResult gateway = queryGateway();
    return connectorFactory.getRuntimeConfigurations().stream()
        .map(config -> toResponse(config, runtimeId, gateway))
        .toList();
  }

  public List<OutboundConnectorResponse> findByType(String type, String runtimeId) {
    GatewayResult gateway = queryGateway();
    var results =
        connectorFactory.getRuntimeConfigurations().stream()
            .filter(config -> config.config().type().equals(type))
            .map(config -> toResponse(config, runtimeId, gateway))
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

  private OutboundConnectorResponse toResponse(
      AbstractConnectorFactory.ConnectorRuntimeConfiguration<OutboundConnectorConfiguration> config,
      String runtimeId,
      GatewayResult gateway) {
    String jobType = config.config().type();
    StreamConnectivity connectivity =
        switch (gateway) {
          case GatewayResult.Failure f -> StreamConnectivity.unavailable(f.gatewayState());
          case GatewayResult.Success s -> StreamConnectivity.compute(jobType, s.streams());
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
        Arrays.asList(connectorConfig.inputVariables()),
        connectorConfig.timeout(),
        config.isActive(),
        runtimeId,
        connectivity.gatewayState(),
        connectivity.brokerState(),
        connectivity.streamIds());
  }
}
