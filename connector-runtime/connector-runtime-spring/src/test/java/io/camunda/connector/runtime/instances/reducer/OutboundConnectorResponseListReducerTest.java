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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.connector.runtime.outbound.controller.OutboundConnectorResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

public class OutboundConnectorResponseListReducerTest {

  private final Reducer<List<OutboundConnectorResponse>> reducer =
      new ReducerRegistry().getReducer(new TypeReference<>() {});

  @Test
  void shouldMergeResponsesFromTwoNodes() {
    var node1 =
        List.of(
            new OutboundConnectorResponse(
                "HTTP JSON",
                "io.camunda:http-json:1",
                List.of("method", "url"),
                30000L,
                true,
                "node-1"),
            new OutboundConnectorResponse(
                "Slack", "io.camunda:slack:1", List.of("channel"), null, true, "node-1"));

    var node2 =
        List.of(
            new OutboundConnectorResponse(
                "HTTP JSON",
                "io.camunda:http-json:1",
                List.of("method", "url"),
                30000L,
                true,
                "node-2"),
            new OutboundConnectorResponse(
                "Slack", "io.camunda:slack:1", List.of("channel"), null, true, "node-2"));

    List<OutboundConnectorResponse> result = reducer.reduce(node1, node2);

    assertEquals(4, result.size());
    assertTrue(result.containsAll(node1));
    assertTrue(result.containsAll(node2));
  }

  @Test
  void shouldReturnOtherNode_whenOneNodeReturnsEmpty() {
    var node1 = List.<OutboundConnectorResponse>of();
    var node2 =
        List.of(
            new OutboundConnectorResponse(
                "HTTP JSON",
                "io.camunda:http-json:1",
                List.of("method", "url"),
                null,
                true,
                "node-2"));

    List<OutboundConnectorResponse> result = reducer.reduce(node1, node2);

    assertEquals(1, result.size());
    assertTrue(result.containsAll(node2));
  }
}
