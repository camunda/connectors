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
package io.camunda.connector.runtime.instances.reducer;

import io.camunda.connector.runtime.core.inbound.ExecutableId;
import io.camunda.connector.runtime.inbound.controller.ActiveInboundConnectorResponse;
import io.camunda.connector.runtime.inbound.executable.ConnectorInstances;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reducer for merging two {@link ConnectorInstances} objects. It combines the instances from both
 * objects, ensuring that if an instance with the same {@link ExecutableId} exists in both, the one
 * with the DOWN status is retained.
 */
public class ConnectorInstancesReducer implements Reducer<ConnectorInstances> {

  private final ActiveInboundConnectorResponseReducer activeInboundConnectorResponseReducer =
      new ActiveInboundConnectorResponseReducer();

  @Override
  public ConnectorInstances reduce(ConnectorInstances a, ConnectorInstances b) {
    if (a == null) {
      return b;
    }
    if (b == null) {
      return a;
    }

    if (!a.connectorId().equals(b.connectorId())) {
      throw new IllegalArgumentException(
          "Cannot reduce ConnectorInstances with different connector IDs: "
              + a.connectorId()
              + " and "
              + b.connectorId());
    }

    Map<ExecutableId, ActiveInboundConnectorResponse> mergedMap = new HashMap<>();

    Stream.concat(a.instances().stream(), b.instances().stream())
        .forEach(
            instance ->
                mergedMap.merge(
                    instance.executableId(),
                    instance,
                    activeInboundConnectorResponseReducer::reduce));

    return new ConnectorInstances(
        a.connectorId(), a.connectorName(), new ArrayList<>(mergedMap.values()));
  }
}
