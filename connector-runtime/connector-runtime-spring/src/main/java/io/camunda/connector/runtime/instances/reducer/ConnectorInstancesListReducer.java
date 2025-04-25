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

import io.camunda.connector.runtime.inbound.executable.ConnectorInstances;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ConnectorInstancesListReducer implements Reducer<List<ConnectorInstances>> {
  private final ConnectorInstancesReducer reducer = new ConnectorInstancesReducer();

  @Override
  public List<ConnectorInstances> reduce(
      List<ConnectorInstances> instances1, List<ConnectorInstances> instances2) {
    if (instances1 == null) {
      return Optional.ofNullable(instances2).orElse(new ArrayList<>());
    }
    if (instances2 == null) {
      return instances1;
    }

    // 1. Merge lists as we might have different connector types in both lists
    var mergedLists = new ArrayList<>(instances1);
    mergedLists.addAll(instances2);

    // 2. Create a map, grouped by connector type. Key will be the connector type, and value will be
    // a list of ConnectorInstances corresponding to that connector type.
    Map<String, List<ConnectorInstances>> groupedByConnectorId =
        mergedLists.stream().collect(Collectors.groupingBy(ConnectorInstances::connectorId));

    // 3. For each ConnectorInstances for a given type, reduce the list using the
    // ConnectorInstancesReducer
    return groupedByConnectorId.values().stream()
        .map(
            connectorInstancesList -> connectorInstancesList.stream().reduce(null, reducer::reduce))
        .collect(Collectors.toList());
  }
}
