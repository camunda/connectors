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

/**
 * Captures the broker-side connectivity state of a single connector job type.
 *
 * <p>Both fields are sourced from the {@code remote} streams queried directly from each broker via
 * the {@code /actuator/jobstreams} monitoring endpoint.
 *
 * @param brokerState whether the connector's stream appears as a consumer on all brokers; {@link
 *     BrokerConnectivityState#UNKNOWN} when broker monitoring is not configured or unreachable
 * @param streamIds consumer IDs observed across all brokers for this job type; {@code null} when
 *     broker monitoring is unavailable or no consumers were found
 */
public record StreamConnectivity(BrokerConnectivityState brokerState, List<String> streamIds) {

  /**
   * Computes the connectivity state for {@code jobType} from the broker remote streams.
   *
   * @param jobType the job type to compute state for
   * @param allRemoteStreams broker-side remote streams; {@link Optional#empty()} when broker
   *     monitoring is not configured or unreachable, yielding {@link
   *     BrokerConnectivityState#UNKNOWN}; a present {@link Optional} with an empty list yields
   *     {@link BrokerConnectivityState#NONE}
   */
  public static StreamConnectivity compute(
      String jobType, Optional<List<RemoteJobStream>> allRemoteStreams) {

    Optional<List<RemoteJobStream>> remoteStreams =
        allRemoteStreams.map(
            streams -> streams.stream().filter(s -> jobType.equals(s.jobType())).toList());

    BrokerConnectivityState brokerState = computeBrokerState(remoteStreams);
    List<String> streamIds = extractStreamIds(remoteStreams);
    return new StreamConnectivity(brokerState, streamIds);
  }

  private static BrokerConnectivityState computeBrokerState(
      Optional<List<RemoteJobStream>> remoteStreams) {
    return remoteStreams
        .map(
            streams -> {
              if (streams.isEmpty()) {
                return BrokerConnectivityState.NONE;
              }
              boolean allConnected =
                  streams.stream()
                      .allMatch(
                          remote -> remote.consumers() != null && !remote.consumers().isEmpty());
              return allConnected
                  ? BrokerConnectivityState.ALL_CONNECTED
                  : BrokerConnectivityState.PARTIALLY_CONNECTED;
            })
        .orElse(BrokerConnectivityState.UNKNOWN);
  }

  private static List<String> extractStreamIds(Optional<List<RemoteJobStream>> remoteStreams) {
    List<String> ids =
        remoteStreams
            .map(
                streams ->
                    streams.stream()
                        .filter(r -> r.consumers() != null)
                        .flatMap(r -> r.consumers().stream())
                        .map(c -> c.get("id"))
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
            .orElse(List.of());
    return ids.isEmpty() ? null : ids;
  }
}
