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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Captures the connectivity state of a single connector job type as observed from both the gateway
 * (client streams) and the brokers (remote streams).
 *
 * <p>Gateway state and broker state are derived from separate sources:
 *
 * <ul>
 *   <li>Gateway state comes from the {@code client} streams of the gateway's {@code
 *       /actuator/jobstreams} endpoint.
 *   <li>Broker state comes from the {@code remote} streams queried directly from each broker (when
 *       broker monitoring is configured and reachable), or falls back to the gateway's own {@code
 *       remote} data (only non-empty when the gateway is embedded in a broker). When neither source
 *       is available, broker state is {@link BrokerConnectivityState#UNKNOWN}.
 * </ul>
 *
 * @param gatewayState whether the connector is registered as a client stream on the gateway
 * @param brokerState whether the gateway stream is propagated to all brokers; {@code null} when
 *     {@code gatewayState} is not {@link GatewayConnectivityState#CONNECTED}
 * @param streamIds the server-side stream IDs observed for this job type; {@code null} when the
 *     gateway is unreachable or not configured
 */
public record StreamConnectivity(
    GatewayConnectivityState gatewayState,
    BrokerConnectivityState brokerState,
    List<String> streamIds) {

  /** Convenience factory for cases where the gateway could not be queried. */
  public static StreamConnectivity unavailable(GatewayConnectivityState gatewayState) {
    return new StreamConnectivity(gatewayState, null, null);
  }

  /**
   * Computes the connectivity state for a given job type from separate gateway and broker data.
   *
   * @param jobType the job type to compute state for
   * @param allClientStreams all client streams from the gateway's response
   * @param allRemoteStreams broker-side remote streams; {@link Optional#empty()} when broker state
   *     cannot be determined (broker monitoring not configured or unavailable, and no embedded
   *     gateway remote data), which yields {@link BrokerConnectivityState#UNKNOWN}; a present
   *     {@link Optional} with an empty list yields {@link BrokerConnectivityState#NONE}
   */
  public static StreamConnectivity compute(
      String jobType,
      List<ClientJobStream> allClientStreams,
      Optional<List<RemoteJobStream>> allRemoteStreams) {

    List<ClientJobStream> clientStreams =
        allClientStreams.stream().filter(s -> jobType.equals(s.jobType())).toList();

    if (clientStreams.isEmpty()) {
      return unavailable(GatewayConnectivityState.NOT_CONNECTED);
    }

    List<String> streamIds =
        clientStreams.stream()
            .map(s -> s.id() != null ? s.id().serverStreamId() : null)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

    Optional<List<RemoteJobStream>> remoteStreams =
        allRemoteStreams.map(
            streams -> streams.stream().filter(s -> jobType.equals(s.jobType())).toList());

    BrokerConnectivityState brokerState = computeBrokerState(Set.copyOf(streamIds), remoteStreams);

    return new StreamConnectivity(
        GatewayConnectivityState.CONNECTED, brokerState, streamIds.isEmpty() ? null : streamIds);
  }

  private static BrokerConnectivityState computeBrokerState(
      Set<String> clientStreamIds, Optional<List<RemoteJobStream>> remoteStreams) {
    return remoteStreams
        .map(
            streams -> {
              if (streams.isEmpty()) {
                return BrokerConnectivityState.NONE;
              }
              boolean allConnected =
                  streams.stream()
                      .allMatch(
                          remote ->
                              remote.consumers() != null
                                  && remote.consumers().stream()
                                      .anyMatch(
                                          consumer ->
                                              clientStreamIds.contains(consumer.get("id"))));
              return allConnected
                  ? BrokerConnectivityState.ALL_CONNECTED
                  : BrokerConnectivityState.PARTIALLY_CONNECTED;
            })
        .orElse(BrokerConnectivityState.UNKNOWN);
  }
}
