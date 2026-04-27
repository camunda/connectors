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
    var jobStreams =
        new JobStreamsResponse(
            List.of(new RemoteJobStream(JOB_TYPE, List.of())), List.of() // no client streams
            );

    var result = StreamConnectivity.compute(JOB_TYPE, jobStreams);

    assertThat(result.gatewayState()).isEqualTo(GatewayConnectivityState.NOT_CONNECTED);
    assertThat(result.brokerState()).isNull();
    assertThat(result.streamIds()).isNull();
  }

  @Test
  void compute_shouldReturnNotConnected_whenClientStreamExistsForOtherTypeOnly() {
    var clientStream =
        new ClientJobStream(OTHER_TYPE, new ClientStreamId(STREAM_ID, 0), List.of(0));
    var jobStreams = new JobStreamsResponse(List.of(), List.of(clientStream));

    var result = StreamConnectivity.compute(JOB_TYPE, jobStreams);

    assertThat(result.gatewayState()).isEqualTo(GatewayConnectivityState.NOT_CONNECTED);
  }

  // ---------------------------------------------------------------------------
  // compute() — client stream present, broker states
  // ---------------------------------------------------------------------------

  @Test
  void compute_shouldReturnNonBrokerState_whenNoRemoteStreams() {
    var clientStream = new ClientJobStream(JOB_TYPE, new ClientStreamId(STREAM_ID, 0), List.of(0));
    var jobStreams = new JobStreamsResponse(List.of(), List.of(clientStream));

    var result = StreamConnectivity.compute(JOB_TYPE, jobStreams);

    assertThat(result.gatewayState()).isEqualTo(GatewayConnectivityState.CONNECTED);
    assertThat(result.brokerState()).isEqualTo(BrokerConnectivityState.NONE);
    assertThat(result.streamIds()).containsExactly(STREAM_ID);
  }

  @Test
  void compute_shouldReturnAllConnected_whenAllBrokersHaveConsumer() {
    var clientStream = new ClientJobStream(JOB_TYPE, new ClientStreamId(STREAM_ID, 0), List.of(0));
    var broker1 = new RemoteJobStream(JOB_TYPE, List.of(Map.of("id", STREAM_ID)));
    var broker2 = new RemoteJobStream(JOB_TYPE, List.of(Map.of("id", STREAM_ID)));
    var jobStreams = new JobStreamsResponse(List.of(broker1, broker2), List.of(clientStream));

    var result = StreamConnectivity.compute(JOB_TYPE, jobStreams);

    assertThat(result.gatewayState()).isEqualTo(GatewayConnectivityState.CONNECTED);
    assertThat(result.brokerState()).isEqualTo(BrokerConnectivityState.ALL_CONNECTED);
    assertThat(result.streamIds()).containsExactly(STREAM_ID);
  }

  @Test
  void compute_shouldReturnPartiallyConnected_whenOnlyOneBrokerHasConsumer() {
    var clientStream = new ClientJobStream(JOB_TYPE, new ClientStreamId(STREAM_ID, 0), List.of(0));
    var connectedBroker = new RemoteJobStream(JOB_TYPE, List.of(Map.of("id", STREAM_ID)));
    var disconnectedBroker = new RemoteJobStream(JOB_TYPE, List.of()); // no consumers
    var jobStreams =
        new JobStreamsResponse(List.of(connectedBroker, disconnectedBroker), List.of(clientStream));

    var result = StreamConnectivity.compute(JOB_TYPE, jobStreams);

    assertThat(result.gatewayState()).isEqualTo(GatewayConnectivityState.CONNECTED);
    assertThat(result.brokerState()).isEqualTo(BrokerConnectivityState.PARTIALLY_CONNECTED);
  }

  @Test
  void compute_shouldReturnPartiallyConnected_whenBrokerConsumerIdDoesNotMatch() {
    var clientStream = new ClientJobStream(JOB_TYPE, new ClientStreamId(STREAM_ID, 0), List.of(0));
    var brokerWithWrongConsumer =
        new RemoteJobStream(JOB_TYPE, List.of(Map.of("id", "other-stream-id")));
    var jobStreams =
        new JobStreamsResponse(List.of(brokerWithWrongConsumer), List.of(clientStream));

    var result = StreamConnectivity.compute(JOB_TYPE, jobStreams);

    assertThat(result.brokerState()).isEqualTo(BrokerConnectivityState.PARTIALLY_CONNECTED);
  }

  // ---------------------------------------------------------------------------
  // compute() — stream ID edge cases
  // ---------------------------------------------------------------------------

  @Test
  void compute_shouldFilterOutNullStreamIds() {
    var clientStreamWithNullId = new ClientJobStream(JOB_TYPE, null, List.of(0));
    var jobStreams = new JobStreamsResponse(List.of(), List.of(clientStreamWithNullId));

    var result = StreamConnectivity.compute(JOB_TYPE, jobStreams);

    // Gateway connected (client stream exists) but streamIds null (all ids were null)
    assertThat(result.gatewayState()).isEqualTo(GatewayConnectivityState.CONNECTED);
    assertThat(result.streamIds()).isNull();
  }

  @Test
  void compute_shouldDeduplicateStreamIds_whenMultipleClientStreamsShareSameId() {
    var stream1 = new ClientJobStream(JOB_TYPE, new ClientStreamId(STREAM_ID, 0), List.of(0));
    var stream2 = new ClientJobStream(JOB_TYPE, new ClientStreamId(STREAM_ID, 1), List.of(1));
    var jobStreams = new JobStreamsResponse(List.of(), List.of(stream1, stream2));

    var result = StreamConnectivity.compute(JOB_TYPE, jobStreams);

    assertThat(result.streamIds()).containsExactly(STREAM_ID);
  }

  @Test
  void compute_shouldOnlyConsiderRemoteStreamsMatchingJobType() {
    var clientStream = new ClientJobStream(JOB_TYPE, new ClientStreamId(STREAM_ID, 0), List.of(0));
    // remote stream is for a different type — should be ignored
    var otherRemote = new RemoteJobStream(OTHER_TYPE, List.of(Map.of("id", STREAM_ID)));
    var jobStreams = new JobStreamsResponse(List.of(otherRemote), List.of(clientStream));

    var result = StreamConnectivity.compute(JOB_TYPE, jobStreams);

    // No remote streams for JOB_TYPE → NONE
    assertThat(result.brokerState()).isEqualTo(BrokerConnectivityState.NONE);
  }
}
