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
package io.camunda.connector.impl.inbound;

import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import java.util.Objects;

public class InboundConnectorConfiguration {

  private final String name;
  private final String type;
  private final Class<? extends InboundConnectorExecutable> connectorClass;

  public InboundConnectorConfiguration(
      final String name,
      final String type,
      final Class<? extends InboundConnectorExecutable> connectorClass) {
    this.name = name;
    this.type = type;
    this.connectorClass = connectorClass;
  }

  public String getName() {
    return name;
  }

  public String getType() {
    return type;
  }

  public Class<? extends InboundConnectorExecutable> getConnectorClass() {
    return this.connectorClass;
  }

  public InboundConnectorConfiguration setConnector(SubscriptionInboundConnector connector) {
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    InboundConnectorConfiguration that = (InboundConnectorConfiguration) o;
    return Objects.equals(type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type);
  }

  @Override
  public String toString() {
    return "InboundConnectorConfiguration{" + "name='" + name + '\'' + ", type='" + type + '}';
  }
}
