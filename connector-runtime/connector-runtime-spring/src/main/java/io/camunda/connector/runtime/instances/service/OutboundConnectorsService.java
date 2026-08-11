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
package io.camunda.connector.runtime.instances.service;

import io.camunda.connector.runtime.core.common.AbstractConnectorFactory;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;
import io.camunda.connector.runtime.inbound.controller.exception.DataNotFoundException;
import io.camunda.connector.runtime.outbound.controller.OutboundConnectorResponse;
import io.camunda.connector.runtime.outbound.jobstream.BrokerJobStreamClient;
import io.camunda.connector.runtime.outbound.jobstream.BrokerStreamsResult;
import io.camunda.connector.runtime.outbound.jobstream.StreamConnectivity;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Backs the {@code /outbound} management endpoints. Every configured physical tenant (engine) runs
 * a job worker for every registered outbound connector, so the registered connectors are listed
 * once per (connector, physical tenant) pair, each entry enriched with the broker/stream
 * connectivity observed on that tenant's own brokers.
 */
public class OutboundConnectorsService {

  private static final Logger LOG = LoggerFactory.getLogger(OutboundConnectorsService.class);

  private final OutboundConnectorFactory connectorFactory;

  /**
   * All configured physical tenants, in iteration order, or a single {@code null} entry for the
   * legacy single-engine wiring (whose responses carry no {@code physicalTenantId}, i.e. exactly
   * the pre-multi-engine response shape). Deliberately kept separate from {@link
   * #brokerJobStreamClientsByPhysicalTenantId}: broker monitoring can be disabled entirely (leaving
   * no clients at all), and the endpoint must still report every engine's connectors.
   */
  private final List<String> physicalTenantIds;

  private final Map<String, BrokerJobStreamClient> brokerJobStreamClientsByPhysicalTenantId;

  /** Used for the {@code null} physical tenant of the legacy single-engine wiring. */
  private final BrokerJobStreamClient legacyBrokerJobStreamClient;

  /** Legacy single-engine constructor without broker monitoring. */
  public OutboundConnectorsService(OutboundConnectorFactory connectorFactory) {
    this(connectorFactory, (BrokerJobStreamClient) null);
  }

  /**
   * Legacy single-engine constructor: one unnamed engine, whose responses carry no {@code
   * physicalTenantId}.
   */
  public OutboundConnectorsService(
      OutboundConnectorFactory connectorFactory, BrokerJobStreamClient brokerJobStreamClient) {
    this.connectorFactory = connectorFactory;
    this.physicalTenantIds = singletonListOfNull();
    this.brokerJobStreamClientsByPhysicalTenantId = Map.of();
    this.legacyBrokerJobStreamClient = brokerJobStreamClient;
  }

  /**
   * @param physicalTenantIds every configured physical tenant (engine); each registered connector
   *     is reported once per entry
   * @param brokerJobStreamClientsByPhysicalTenantId one broker job-stream client per physical
   *     tenant, or empty when broker monitoring is disabled. Tenants missing from this map report
   *     {@link io.camunda.connector.runtime.outbound.jobstream.BrokerConnectivityState#UNKNOWN}.
   */
  public OutboundConnectorsService(
      OutboundConnectorFactory connectorFactory,
      Collection<String> physicalTenantIds,
      Map<String, BrokerJobStreamClient> brokerJobStreamClientsByPhysicalTenantId) {
    this.connectorFactory = connectorFactory;
    this.physicalTenantIds =
        physicalTenantIds == null || physicalTenantIds.isEmpty()
            ? singletonListOfNull()
            : List.copyOf(physicalTenantIds);
    this.brokerJobStreamClientsByPhysicalTenantId =
        brokerJobStreamClientsByPhysicalTenantId == null
            ? Map.of()
            : brokerJobStreamClientsByPhysicalTenantId;
    this.legacyBrokerJobStreamClient = null;
  }

  public List<OutboundConnectorResponse> findAll(String runtimeId) {
    return findAll(runtimeId, null);
  }

  /**
   * @param physicalTenantIdFilter when non-null and non-empty, restricts the result to these
   *     physical tenants (engines); {@code null}/empty returns every configured engine, matching
   *     the pre-existing (unfiltered) behavior of this endpoint.
   */
  public List<OutboundConnectorResponse> findAll(
      String runtimeId, List<String> physicalTenantIdFilter) {
    var brokerStreams = queryBrokerStreams(physicalTenantIdFilter);
    var configurations = connectorFactory.getRuntimeConfigurations();
    var results = new ArrayList<OutboundConnectorResponse>();
    brokerStreams.forEach(
        (physicalTenantId, streams) ->
            configurations.forEach(
                config -> results.add(toResponse(config, runtimeId, physicalTenantId, streams))));
    return results;
  }

  public List<OutboundConnectorResponse> findByType(String type, String runtimeId) {
    return findByType(type, runtimeId, null);
  }

  /**
   * @param physicalTenantIdFilter see {@link #findAll(String, List)}
   */
  public List<OutboundConnectorResponse> findByType(
      String type, String runtimeId, List<String> physicalTenantIdFilter) {
    var results =
        findAll(runtimeId, physicalTenantIdFilter).stream()
            .filter(response -> response.type().equals(type))
            .toList();
    if (results.isEmpty()) {
      throw new DataNotFoundException(OutboundConnectorResponse.class, type);
    }
    return results;
  }

  /**
   * Queries every requested physical tenant's brokers, keeping the results attributed to their
   * engine of origin. A tenant whose brokers cannot be reached (or that has no broker monitoring
   * configured at all) is still present in the returned map, with an empty value — its connectors
   * are reported with an {@code UNKNOWN} connectivity state rather than dropped from the listing.
   */
  private Map<String, Optional<BrokerStreamsResult>> queryBrokerStreams(
      List<String> physicalTenantIdFilter) {
    var streamsByPhysicalTenantId = new LinkedHashMap<String, Optional<BrokerStreamsResult>>();
    for (String physicalTenantId : physicalTenantIds) {
      if (!matchesFilter(physicalTenantId, physicalTenantIdFilter)) {
        continue;
      }
      streamsByPhysicalTenantId.put(
          physicalTenantId, fetchRemoteStreams(physicalTenantId, clientFor(physicalTenantId)));
    }
    return streamsByPhysicalTenantId;
  }

  private BrokerJobStreamClient clientFor(String physicalTenantId) {
    return physicalTenantId == null
        ? legacyBrokerJobStreamClient
        : brokerJobStreamClientsByPhysicalTenantId.get(physicalTenantId);
  }

  /** Null-tolerant on both sides: an immutable {@code List.of(...)} filter rejects {@code null}. */
  private static boolean matchesFilter(String physicalTenantId, List<String> filter) {
    return filter == null
        || filter.isEmpty()
        || filter.stream().anyMatch(id -> Objects.equals(id, physicalTenantId));
  }

  private Optional<BrokerStreamsResult> fetchRemoteStreams(
      String physicalTenantId, BrokerJobStreamClient brokerJobStreamClient) {
    if (brokerJobStreamClient == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(brokerJobStreamClient.fetchRemoteStreams());
    } catch (Exception e) {
      LOG.warn(
          "Failed to fetch remote streams from brokers of physical tenant '{}': {}",
          physicalTenantId,
          e.getMessage());
      return Optional.empty();
    }
  }

  private OutboundConnectorResponse toResponse(
      AbstractConnectorFactory.ConnectorRuntimeConfiguration<OutboundConnectorConfiguration> config,
      String runtimeId,
      String physicalTenantId,
      Optional<BrokerStreamsResult> brokerStreams) {
    var connectivity = StreamConnectivity.compute(config.config().type(), brokerStreams);
    var connectorConfig = config.config();
    return new OutboundConnectorResponse(
        connectorConfig.name(),
        connectorConfig.type(),
        connectorConfig.inputVariables() == null
            ? List.of()
            : List.of(connectorConfig.inputVariables()),
        connectorConfig.timeout(),
        config.isActive(),
        runtimeId,
        connectivity.brokerState(),
        connectivity.streamIds(),
        physicalTenantId);
  }

  /** {@code List.of} rejects null elements, hence the explicit singleton construction. */
  private static List<String> singletonListOfNull() {
    var list = new ArrayList<String>(1);
    list.add(null);
    return list;
  }
}
