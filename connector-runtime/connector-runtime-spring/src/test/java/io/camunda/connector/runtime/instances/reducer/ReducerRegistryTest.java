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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.connector.runtime.inbound.executable.ConnectorInstances;
import io.camunda.connector.runtime.metrics.InboundConnectorMetrics;
import io.camunda.connector.runtime.metrics.OutboundConnectorMetrics;
import io.camunda.connector.runtime.outbound.controller.OutboundConnectorResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ReducerRegistryTest {
  private final ReducerRegistry reducerRegistry = new ReducerRegistry();

  @Test
  public void shouldReturnReducer_whenClassParameter() {
    // given
    var targetClass = new TypeReference<ConnectorInstances>() {};

    // when
    var reducer = reducerRegistry.getReducer(targetClass);

    // then
    assertThat(reducer).isNotNull().isInstanceOf(ConnectorInstancesReducer.class);
  }

  @Test
  public void shouldReturnReducer_whenListParameter() {
    // given
    var targetClass = new TypeReference<List<ConnectorInstances>>() {};

    // when
    var reducer = reducerRegistry.getReducer(targetClass);

    // then
    assertThat(reducer).isNotNull().isInstanceOf(ConnectorInstancesListReducer.class);
  }

  @Test
  public void shouldReturnReducer_forOutboundConnectorResponseList() {
    // given
    var targetClass = new TypeReference<List<OutboundConnectorResponse>>() {};

    // when
    var reducer = reducerRegistry.getReducer(targetClass);

    // then
    assertThat(reducer).isNotNull();
  }

  @Test
  public void shouldReturnReducer_forOutboundConnectorMetricsList() {
    var reducer =
        reducerRegistry.getReducer(new TypeReference<List<OutboundConnectorMetrics>>() {});
    assertThat(reducer).isNotNull();
  }

  @Test
  public void shouldReturnReducer_forInboundConnectorMetricsList() {
    var reducer = reducerRegistry.getReducer(new TypeReference<List<InboundConnectorMetrics>>() {});
    assertThat(reducer).isNotNull();
  }
}
