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
          new TypeReference<List<InstanceAwareModel.InstanceAwareActivity>>() {}.getType(),
          Reducers.mergeListsReducer(),
          new TypeReference<
              List<Collection<InstanceAwareModel.InstanceAwareActivity>>>() {}.getType(),
          Reducers.mergeListsReducer(),
          new TypeReference<List<InstanceAwareModel.InstanceAwareHealth>>() {}.getType(),
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
