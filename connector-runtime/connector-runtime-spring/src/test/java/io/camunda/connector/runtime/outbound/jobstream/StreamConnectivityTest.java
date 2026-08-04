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
        StreamConnectivity.compute(JOB_TYPE, Optional.of(new BrokerStreamsResult(List.of())));

    assertThat(result.brokerState()).isEqualTo(BrokerConnectivityState.NONE);
    assertThat(result.streamIds()).isNull();
  }

  @Test
  void compute_shouldReturnNone_whenBrokerHasNoConsumersForJobType() {
    var emptyBroker = new RemoteJobStream(JOB_TYPE, List.of());

    var result =
        StreamConnectivity.compute(
            JOB_TYPE, Optional.of(new BrokerStreamsResult(List.of(List.of(emptyBroker)))));

    assertThat(result.brokerState()).isEqualTo(BrokerConnectivityState.NONE);
    assertThat(result.streamIds()).isNull();
  }

  @Test
  void compute_shouldReturnNone_whenBrokerConsumersHaveNullId() {
    // Consumer entry exists but has no "id" key — should not count as connected
    var brokerWithNullIdConsumer = new RemoteJobStream(JOB_TYPE, List.of(Map.of("other", "value")));

    var result =
        StreamConnectivity.compute(
            JOB_TYPE,
            Optional.of(new BrokerStreamsResult(List.of(List.of(brokerWithNullIdConsumer)))));

    assertThat(result.brokerState()).isEqualTo(BrokerConnectivityState.NONE);
    assertThat(result.streamIds()).isNull();
  }

  @Test
  void compute_shouldReturnAllConnected_whenAllBrokersHaveConsumer() {
    var broker1 = new RemoteJobStream(JOB_TYPE, List.of(Map.of("id", STREAM_ID_1)));
    var broker2 = new RemoteJobStream(JOB_TYPE, List.of(Map.of("id", STREAM_ID_1)));

    var result =
        StreamConnectivity.compute(
            JOB_TYPE,
            Optional.of(new BrokerStreamsResult(List.of(List.of(broker1), List.of(broker2)))));

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
            Optional.of(
                new BrokerStreamsResult(
                    List.of(List.of(connectedBroker), List.of(disconnectedBroker)))));

    assertThat(result.brokerState()).isEqualTo(BrokerConnectivityState.PARTIALLY_CONNECTED);
  }

  @Test
  void compute_shouldReturnPartiallyConnected_whenOneOfTwoBrokersDoesNotReportJobType() {
    // broker2 has no entry for JOB_TYPE at all (empty sublist); totalBrokerCount=2
    var broker1 = new RemoteJobStream(JOB_TYPE, List.of(Map.of("id", STREAM_ID_1)));

    var result =
        StreamConnectivity.compute(
            JOB_TYPE, Optional.of(new BrokerStreamsResult(List.of(List.of(broker1), List.of()))));

    assertThat(result.brokerState()).isEqualTo(BrokerConnectivityState.PARTIALLY_CONNECTED);
  }

  @Test
  void
      compute_shouldReturnAllConnected_whenSingleBrokerSplitsJobTypeAcrossMultipleEntries_insteadOfOneEntryWithMultipleConsumers() {
    // Reproduces the reported bug: one broker's /actuator/jobstreams reports the SAME jobType as
    // two separate entries (one consumer each, e.g. due to fetchVariables ordering differences
    // between gateway registrations), rather than one entry listing both consumers. With only one
    // broker queried, this must still be ALL_CONNECTED, not PARTIALLY_CONNECTED.
    var entry1 = new RemoteJobStream(JOB_TYPE, List.of(Map.of("id", STREAM_ID_1)));
    var entry2 = new RemoteJobStream(JOB_TYPE, List.of(Map.of("id", STREAM_ID_2)));

    var result =
        StreamConnectivity.compute(
            JOB_TYPE, Optional.of(new BrokerStreamsResult(List.of(List.of(entry1, entry2)))));

    assertThat(result.brokerState()).isEqualTo(BrokerConnectivityState.ALL_CONNECTED);
    // Both consumer ids are real gateway receivers and must still be surfaced.
    assertThat(result.streamIds()).containsExactlyInAnyOrder(STREAM_ID_1, STREAM_ID_2);
  }

  @Test
  void
      compute_shouldReturnPartiallyConnected_whenOnlyOneOfTwoBrokersSplitsJobTypeAcrossMultipleEntries() {
    // Same split-entry quirk as above, but now on only one of two brokers — the split must not
    // make brokersWithValidConsumer (2 entries) exceed totalBrokerCount (2 brokers) and flip to
    // ALL_CONNECTED; it must correctly report PARTIALLY_CONNECTED since broker2 has no consumer.
    var broker1Entry1 = new RemoteJobStream(JOB_TYPE, List.of(Map.of("id", STREAM_ID_1)));
    var broker1Entry2 = new RemoteJobStream(JOB_TYPE, List.of(Map.of("id", STREAM_ID_2)));
    var broker2Disconnected = new RemoteJobStream(JOB_TYPE, List.of());

    var result =
        StreamConnectivity.compute(
            JOB_TYPE,
            Optional.of(
                new BrokerStreamsResult(
                    List.of(List.of(broker1Entry1, broker1Entry2), List.of(broker2Disconnected)))));

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
            JOB_TYPE,
            Optional.of(new BrokerStreamsResult(List.of(List.of(broker1), List.of(broker2)))));

    assertThat(result.streamIds()).containsExactly(STREAM_ID_1);
  }

  @Test
  void compute_shouldCollectDistinctStreamIds_acrossBrokers() {
    var broker1 = new RemoteJobStream(JOB_TYPE, List.of(Map.of("id", STREAM_ID_1)));
    var broker2 = new RemoteJobStream(JOB_TYPE, List.of(Map.of("id", STREAM_ID_2)));

    var result =
        StreamConnectivity.compute(
            JOB_TYPE,
            Optional.of(new BrokerStreamsResult(List.of(List.of(broker1), List.of(broker2)))));

    assertThat(result.streamIds()).containsExactlyInAnyOrder(STREAM_ID_1, STREAM_ID_2);
  }

  @Test
  void compute_shouldReturnNullStreamIds_whenNoConsumersFound() {
    var emptyBroker = new RemoteJobStream(JOB_TYPE, List.of());

    var result =
        StreamConnectivity.compute(
            JOB_TYPE, Optional.of(new BrokerStreamsResult(List.of(List.of(emptyBroker)))));

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
            JOB_TYPE, Optional.of(new BrokerStreamsResult(List.of(List.of(otherRemote)))));

    // Remote streams exist but none match JOB_TYPE → NONE
    assertThat(result.brokerState()).isEqualTo(BrokerConnectivityState.NONE);
    assertThat(result.streamIds()).isNull();
  }
}
