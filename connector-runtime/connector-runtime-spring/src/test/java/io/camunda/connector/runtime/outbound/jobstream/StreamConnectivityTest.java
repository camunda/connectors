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
package io.camunda.connector.runtime.outbound.jobstream;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StreamConnectivityTest {

  private static final String JOB_TYPE = "io.camunda:http-json:1";
  private static final String OTHER_TYPE = "io.camunda:rabbitmq:1";
  private static final String STREAM_ID = "stream-abc-123";

  // ---------------------------------------------------------------------------
  // unavailable()
  // ---------------------------------------------------------------------------

  @Test
  void unavailable_shouldSetGatewayStateAndNullFields() {
    var result = StreamConnectivity.unavailable(GatewayConnectivityState.UNREACHABLE);

    assertThat(result.gatewayState()).isEqualTo(GatewayConnectivityState.UNREACHABLE);
    assertThat(result.brokerState()).isNull();
    assertThat(result.streamIds()).isNull();
  }

  // ---------------------------------------------------------------------------
  // compute() — no client stream
  // ---------------------------------------------------------------------------

  @Test
  void compute_shouldReturnNotConnected_whenNoClientStreamForJobType() {
    var result = StreamConnectivity.compute(JOB_TYPE, List.of(), Optional.empty());

    assertThat(result.gatewayState()).isEqualTo(GatewayConnectivityState.NOT_CONNECTED);
    assertThat(result.brokerState()).isNull();
    assertThat(result.streamIds()).isNull();
  }

  @Test
  void compute_shouldReturnNotConnected_whenClientStreamExistsForOtherTypeOnly() {
    var clientStream =
        new ClientJobStream(OTHER_TYPE, new ClientStreamId(STREAM_ID, 0), List.of(0));

    var result = StreamConnectivity.compute(JOB_TYPE, List.of(clientStream), Optional.empty());

    assertThat(result.gatewayState()).isEqualTo(GatewayConnectivityState.NOT_CONNECTED);
  }

  // ---------------------------------------------------------------------------
  // compute() — client stream present, broker states
  // ---------------------------------------------------------------------------

  @Test
  void compute_shouldReturnUnknown_whenRemoteStreamsAbsent() {
    var clientStream = new ClientJobStream(JOB_TYPE, new ClientStreamId(STREAM_ID, 0), List.of(0));

    var result = StreamConnectivity.compute(JOB_TYPE, List.of(clientStream), Optional.empty());

    assertThat(result.gatewayState()).isEqualTo(GatewayConnectivityState.CONNECTED);
    assertThat(result.brokerState()).isEqualTo(BrokerConnectivityState.UNKNOWN);
    assertThat(result.streamIds()).containsExactly(STREAM_ID);
  }

  @Test
  void compute_shouldReturnNone_whenBrokerStreamsExplicitlyEmpty() {
    var clientStream = new ClientJobStream(JOB_TYPE, new ClientStreamId(STREAM_ID, 0), List.of(0));

    var result =
        StreamConnectivity.compute(JOB_TYPE, List.of(clientStream), Optional.of(List.of()));

    assertThat(result.gatewayState()).isEqualTo(GatewayConnectivityState.CONNECTED);
    assertThat(result.brokerState()).isEqualTo(BrokerConnectivityState.NONE);
    assertThat(result.streamIds()).containsExactly(STREAM_ID);
  }

  @Test
  void compute_shouldReturnAllConnected_whenAllBrokersHaveConsumer() {
    var clientStream = new ClientJobStream(JOB_TYPE, new ClientStreamId(STREAM_ID, 0), List.of(0));
    var broker1 = new RemoteJobStream(JOB_TYPE, List.of(Map.of("id", STREAM_ID)));
    var broker2 = new RemoteJobStream(JOB_TYPE, List.of(Map.of("id", STREAM_ID)));

    var result =
        StreamConnectivity.compute(
            JOB_TYPE, List.of(clientStream), Optional.of(List.of(broker1, broker2)));

    assertThat(result.gatewayState()).isEqualTo(GatewayConnectivityState.CONNECTED);
    assertThat(result.brokerState()).isEqualTo(BrokerConnectivityState.ALL_CONNECTED);
    assertThat(result.streamIds()).containsExactly(STREAM_ID);
  }

  @Test
  void compute_shouldReturnPartiallyConnected_whenOnlyOneBrokerHasConsumer() {
    var clientStream = new ClientJobStream(JOB_TYPE, new ClientStreamId(STREAM_ID, 0), List.of(0));
    var connectedBroker = new RemoteJobStream(JOB_TYPE, List.of(Map.of("id", STREAM_ID)));
    var disconnectedBroker = new RemoteJobStream(JOB_TYPE, List.of()); // no consumers

    var result =
        StreamConnectivity.compute(
            JOB_TYPE,
            List.of(clientStream),
            Optional.of(List.of(connectedBroker, disconnectedBroker)));

    assertThat(result.gatewayState()).isEqualTo(GatewayConnectivityState.CONNECTED);
    assertThat(result.brokerState()).isEqualTo(BrokerConnectivityState.PARTIALLY_CONNECTED);
  }

  @Test
  void compute_shouldReturnPartiallyConnected_whenBrokerConsumerIdDoesNotMatch() {
    var clientStream = new ClientJobStream(JOB_TYPE, new ClientStreamId(STREAM_ID, 0), List.of(0));
    var brokerWithWrongConsumer =
        new RemoteJobStream(JOB_TYPE, List.of(Map.of("id", "other-stream-id")));

    var result =
        StreamConnectivity.compute(
            JOB_TYPE, List.of(clientStream), Optional.of(List.of(brokerWithWrongConsumer)));

    assertThat(result.brokerState()).isEqualTo(BrokerConnectivityState.PARTIALLY_CONNECTED);
  }

  // ---------------------------------------------------------------------------
  // compute() — stream ID edge cases
  // ---------------------------------------------------------------------------

  @Test
  void compute_shouldFilterOutNullStreamIds() {
    var clientStreamWithNullId = new ClientJobStream(JOB_TYPE, null, List.of(0));

    var result =
        StreamConnectivity.compute(JOB_TYPE, List.of(clientStreamWithNullId), Optional.empty());

    // Gateway connected (client stream exists) but streamIds null (all ids were null)
    assertThat(result.gatewayState()).isEqualTo(GatewayConnectivityState.CONNECTED);
    assertThat(result.streamIds()).isNull();
  }

  @Test
  void compute_shouldDeduplicateStreamIds_whenMultipleClientStreamsShareSameId() {
    var stream1 = new ClientJobStream(JOB_TYPE, new ClientStreamId(STREAM_ID, 0), List.of(0));
    var stream2 = new ClientJobStream(JOB_TYPE, new ClientStreamId(STREAM_ID, 1), List.of(1));

    var result = StreamConnectivity.compute(JOB_TYPE, List.of(stream1, stream2), Optional.empty());

    assertThat(result.streamIds()).containsExactly(STREAM_ID);
  }

  @Test
  void compute_shouldOnlyConsiderRemoteStreamsMatchingJobType() {
    var clientStream = new ClientJobStream(JOB_TYPE, new ClientStreamId(STREAM_ID, 0), List.of(0));
    // remote stream is for a different type — should be filtered out
    var otherRemote = new RemoteJobStream(OTHER_TYPE, List.of(Map.of("id", STREAM_ID)));

    var result =
        StreamConnectivity.compute(
            JOB_TYPE, List.of(clientStream), Optional.of(List.of(otherRemote)));

    // No remote streams for JOB_TYPE → NONE (we queried but found nothing for this type)
    assertThat(result.brokerState()).isEqualTo(BrokerConnectivityState.NONE);
  }
}
