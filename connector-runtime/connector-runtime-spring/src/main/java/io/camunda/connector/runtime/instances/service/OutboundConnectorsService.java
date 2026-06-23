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
import io.camunda.connector.runtime.outbound.jobstream.RemoteJobStream;
import io.camunda.connector.runtime.outbound.jobstream.StreamConnectivity;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutboundConnectorsService {

  private static final Logger LOG = LoggerFactory.getLogger(OutboundConnectorsService.class);

  private final OutboundConnectorFactory connectorFactory;
  private final BrokerJobStreamClient brokerJobStreamClient;

  public OutboundConnectorsService(OutboundConnectorFactory connectorFactory) {
    this(connectorFactory, null);
  }

  public OutboundConnectorsService(
      OutboundConnectorFactory connectorFactory, BrokerJobStreamClient brokerJobStreamClient) {
    this.connectorFactory = connectorFactory;
    this.brokerJobStreamClient = brokerJobStreamClient;
  }

  public List<OutboundConnectorResponse> findAll(String runtimeId) {
    Optional<List<RemoteJobStream>> remoteStreams = queryBrokerStreams();
    return connectorFactory.getRuntimeConfigurations().stream()
        .map(config -> toResponse(config, runtimeId, remoteStreams))
        .toList();
  }

  public List<OutboundConnectorResponse> findByType(String type, String runtimeId) {
    Optional<List<RemoteJobStream>> remoteStreams = queryBrokerStreams();
    var results =
        connectorFactory.getRuntimeConfigurations().stream()
            .filter(config -> config.config().type().equals(type))
            .map(config -> toResponse(config, runtimeId, remoteStreams))
            .toList();
    if (results.isEmpty()) {
      throw new DataNotFoundException(OutboundConnectorResponse.class, type);
    }
    return results;
  }

  private Optional<List<RemoteJobStream>> queryBrokerStreams() {
    if (brokerJobStreamClient == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(brokerJobStreamClient.fetchRemoteStreams());
    } catch (Exception e) {
      LOG.warn("Failed to fetch remote streams from brokers: {}", e.getMessage());
      return Optional.empty();
    }
  }

  private OutboundConnectorResponse toResponse(
      AbstractConnectorFactory.ConnectorRuntimeConfiguration<OutboundConnectorConfiguration> config,
      String runtimeId,
      Optional<List<RemoteJobStream>> remoteStreams) {
    var connectivity = StreamConnectivity.compute(config.config().type(), remoteStreams);
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
        connectivity.brokerState(),
        connectivity.streamIds());
  }
}
