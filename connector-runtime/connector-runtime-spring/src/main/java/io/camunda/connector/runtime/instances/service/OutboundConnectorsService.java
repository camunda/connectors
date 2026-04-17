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
import java.util.Arrays;
import java.util.List;

public class OutboundConnectorsService {

  private final OutboundConnectorFactory connectorFactory;

  public OutboundConnectorsService(OutboundConnectorFactory connectorFactory) {
    this.connectorFactory = connectorFactory;
  }

  public List<OutboundConnectorResponse> findAll(String runtimeId) {
    return connectorFactory.getRuntimeConfigurations().stream()
        .map(config -> toResponse(config, runtimeId))
        .toList();
  }

  public List<OutboundConnectorResponse> findByType(String type, String runtimeId) {
    var results =
        connectorFactory.getRuntimeConfigurations().stream()
            .filter(config -> config.config().type().equals(type))
            .map(config -> toResponse(config, runtimeId))
            .toList();
    if (results.isEmpty()) {
      throw new DataNotFoundException(OutboundConnectorResponse.class, type);
    }
    return results;
  }

  private OutboundConnectorResponse toResponse(
      AbstractConnectorFactory.ConnectorRuntimeConfiguration<OutboundConnectorConfiguration> config,
      String runtimeId) {
    var connectorConfig = config.config();
    return new OutboundConnectorResponse(
        connectorConfig.name(),
        connectorConfig.type(),
        Arrays.asList(connectorConfig.inputVariables()),
        connectorConfig.timeout(),
        config.isActive(),
        runtimeId);
  }
}
