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
package io.camunda.connector.runtime.core.config;

import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import java.util.List;
import java.util.function.Supplier;

public record InboundConnectorConfiguration(
    String name,
    String type,
    Class<? extends InboundConnectorExecutable> connectorClass,
    Supplier<InboundConnectorExecutable> customInstanceSupplier,
    List<String> deduplicationProperties)
    implements ConnectorConfiguration {

  public InboundConnectorConfiguration(
      String name,
      String type,
      Class<? extends InboundConnectorExecutable> connectorClass,
      List<String> deduplicationProperties) {
    this(name, type, connectorClass, null, deduplicationProperties);
  }

  @Override
  public ConnectorDirection direction() {
    return ConnectorDirection.INBOUND;
  }
}
