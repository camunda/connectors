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
  private static final String STREAM_ID_1 = "stream-abc-123";
  private static final String STREAM_ID_2 = "stream-def-456";

  // ---------------------------------------------------------------------------
  // compute() — broker monitoring not configured / unreachable
  // ---------------------------------------------------------------------------

  @Test
  void compute_shouldReturnUnknown_whenRemoteStreamsAbsent() {
    var result = StreamConnectivity.compute(JOB_TYPE, Optional.empty());

    assertThat(result.brokerState()).isEqualTo(BrokerConnectivityState.UNKNOWN);
    assertThat(result.streamIds()).isNull();
  }

  // ---------------------------------------------------------------------------
  // compute() — broker returns data
  // ---------------------------------------------------------------------------

  @Test
  void compute_shouldReturnNone_whenBrokerStreamsExplicitlyEmpty() {
    var result =
        StreamConnectivity.compute(JOB_TYPE, Optional.of(new BrokerStreamsResult(List.of(), 0)));

    assertThat(result.brokerState()).isEqualTo(BrokerConnectivityState.NONE);
    assertThat(result.streamIds()).isNull();
  }

  @Test
  void compute_shouldReturnNone_whenBrokerHasNoConsumersForJobType() {
    var emptyBroker = new RemoteJobStream(JOB_TYPE, List.of());

    var result =
        StreamConnectivity.compute(
            JOB_TYPE, Optional.of(new BrokerStreamsResult(List.of(emptyBroker), 1)));

    assertThat(result.brokerState()).isEqualTo(BrokerConnectivityState.NONE);
    assertThat(result.streamIds()).isNull();
  }

  @Test
  void compute_shouldReturnNone_whenBrokerConsumersHaveNullId() {
    // Consumer entry exists but has no "id" key — should not count as connected
    var brokerWithNullIdConsumer = new RemoteJobStream(JOB_TYPE, List.of(Map.of("other", "value")));

    var result =
        StreamConnectivity.compute(
            JOB_TYPE, Optional.of(new BrokerStreamsResult(List.of(brokerWithNullIdConsumer), 1)));

    assertThat(result.brokerState()).isEqualTo(BrokerConnectivityState.NONE);
    assertThat(result.streamIds()).isNull();
  }

  @Test
  void compute_shouldReturnAllConnected_whenAllBrokersHaveConsumer() {
    var broker1 = new RemoteJobStream(JOB_TYPE, List.of(Map.of("id", STREAM_ID_1)));
    var broker2 = new RemoteJobStream(JOB_TYPE, List.of(Map.of("id", STREAM_ID_1)));

    var result =
        StreamConnectivity.compute(
            JOB_TYPE, Optional.of(new BrokerStreamsResult(List.of(broker1, broker2), 2)));

    assertThat(result.brokerState()).isEqualTo(BrokerConnectivityState.ALL_CONNECTED);
    assertThat(result.streamIds()).containsExactly(STREAM_ID_1);
  }

  @Test
  void compute_shouldReturnPartiallyConnected_whenOnlyOneBrokerHasConsumer() {
    var connectedBroker = new RemoteJobStream(JOB_TYPE, List.of(Map.of("id", STREAM_ID_1)));
    var disconnectedBroker = new RemoteJobStream(JOB_TYPE, List.of());

    var result =
        StreamConnectivity.compute(
            JOB_TYPE,
            Optional.of(new BrokerStreamsResult(List.of(connectedBroker, disconnectedBroker), 2)));

    assertThat(result.brokerState()).isEqualTo(BrokerConnectivityState.PARTIALLY_CONNECTED);
  }

  @Test
  void compute_shouldReturnPartiallyConnected_whenOneOfTwoBrokersDoesNotReportJobType() {
    // broker2 has no entry for JOB_TYPE at all (absent from streams); totalBrokerCount=2
    var broker1 = new RemoteJobStream(JOB_TYPE, List.of(Map.of("id", STREAM_ID_1)));

    var result =
        StreamConnectivity.compute(
            JOB_TYPE, Optional.of(new BrokerStreamsResult(List.of(broker1), 2)));

    assertThat(result.brokerState()).isEqualTo(BrokerConnectivityState.PARTIALLY_CONNECTED);
  }

  // ---------------------------------------------------------------------------
  // compute() — streamIds
  // ---------------------------------------------------------------------------

  @Test
  void compute_shouldDeduplicateStreamIds_whenMultipleBrokersReportSameConsumer() {
    var broker1 = new RemoteJobStream(JOB_TYPE, List.of(Map.of("id", STREAM_ID_1)));
    var broker2 = new RemoteJobStream(JOB_TYPE, List.of(Map.of("id", STREAM_ID_1)));

    var result =
        StreamConnectivity.compute(
            JOB_TYPE, Optional.of(new BrokerStreamsResult(List.of(broker1, broker2), 2)));

    assertThat(result.streamIds()).containsExactly(STREAM_ID_1);
  }

  @Test
  void compute_shouldCollectDistinctStreamIds_acrossBrokers() {
    var broker1 = new RemoteJobStream(JOB_TYPE, List.of(Map.of("id", STREAM_ID_1)));
    var broker2 = new RemoteJobStream(JOB_TYPE, List.of(Map.of("id", STREAM_ID_2)));

    var result =
        StreamConnectivity.compute(
            JOB_TYPE, Optional.of(new BrokerStreamsResult(List.of(broker1, broker2), 2)));

    assertThat(result.streamIds()).containsExactlyInAnyOrder(STREAM_ID_1, STREAM_ID_2);
  }

  @Test
  void compute_shouldReturnNullStreamIds_whenNoConsumersFound() {
    var emptyBroker = new RemoteJobStream(JOB_TYPE, List.of());

    var result =
        StreamConnectivity.compute(
            JOB_TYPE, Optional.of(new BrokerStreamsResult(List.of(emptyBroker), 1)));

    assertThat(result.streamIds()).isNull();
  }

  // ---------------------------------------------------------------------------
  // compute() — job type filtering
  // ---------------------------------------------------------------------------

  @Test
  void compute_shouldOnlyConsiderRemoteStreamsMatchingJobType() {
    var otherRemote = new RemoteJobStream(OTHER_TYPE, List.of(Map.of("id", STREAM_ID_1)));

    var result =
        StreamConnectivity.compute(
            JOB_TYPE, Optional.of(new BrokerStreamsResult(List.of(otherRemote), 1)));

    // Remote streams exist but none match JOB_TYPE → NONE
    assertThat(result.brokerState()).isEqualTo(BrokerConnectivityState.NONE);
    assertThat(result.streamIds()).isNull();
  }
}
