package io.camunda.connector.runtime.instances.reducer;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.connector.runtime.inbound.controller.ActiveInboundConnectorResponse;
import io.camunda.connector.runtime.inbound.executable.ConnectorInstances;
import io.camunda.connector.runtime.instances.InstanceAwareModel;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class ReducerRegistry {
  private final Map<Type, Reducer<?>> reducers =
      Map.of(
          new TypeReference<ConnectorInstances>() {}.getType(),
          new ConnectorInstancesReducer(),
          new TypeReference<List<ConnectorInstances>>() {}.getType(),
          new ConnectorInstancesListReducer(),
          new TypeReference<ActiveInboundConnectorResponse>() {}.getType(),
          new ActiveInboundConnectorResponseReducer(),
          new TypeReference<Collection<InstanceAwareModel.InstanceAwareActivity>>() {}.getType(),
          Reducers.mergeListsReducer(),
          new TypeReference<Collection<InstanceAwareModel.InstanceAwareHealth>>() {}.getType(),
          Reducers.mergeListsReducer());

  @SuppressWarnings("unchecked")
  public <T> Reducer<T> getReducer(TypeReference<T> typeRef) {
    Reducer<?> reducer = reducers.get(typeRef.getType());
    if (reducer == null) {
      throw new IllegalArgumentException(
          "No reducer found for type: " + typeRef.getType() + ". Did you register it?");
    }
    return (Reducer<T>) reducer;
  }
}
