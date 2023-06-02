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
package io.camunda.connector.runtime.core.discovery;

import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.impl.ConnectorUtil;
import io.camunda.connector.impl.inbound.InboundConnectorConfiguration;
import io.camunda.connector.impl.outbound.OutboundConnectorConfiguration;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

/** Static functionality for SPI (auto) discovery */
public class SPIConnectorDiscovery {

  public static List<OutboundConnectorConfiguration> discoverOutbound() {
    return ServiceLoader.load(OutboundConnectorFunction.class).stream()
        .map(
            functionProvider -> {
              Class<? extends OutboundConnectorFunction> cls = functionProvider.type();
              return ConnectorUtil.getRequiredOutboundConnectorConfiguration(cls);
            })
        .collect(Collectors.toList());
  }

  public static List<InboundConnectorConfiguration> discoverInbound() {
    return ServiceLoader.load(InboundConnectorExecutable.class).stream()
        .map(
            functionProvider -> {
              Class<? extends InboundConnectorExecutable> cls = functionProvider.type();
              return ConnectorUtil.getRequiredInboundConnectorConfiguration(cls);
            })
        .collect(Collectors.toList());
  }
}
