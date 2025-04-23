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
