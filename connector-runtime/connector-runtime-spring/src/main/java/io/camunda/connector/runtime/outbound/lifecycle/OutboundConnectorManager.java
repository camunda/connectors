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
package io.camunda.connector.runtime.outbound.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.value.JobWorkerValue;
import io.camunda.client.annotation.value.SourceAware;
import io.camunda.client.annotation.value.SourceAware.FromAnnotation;
import io.camunda.client.jobhandling.JobCallbackCommandWrapperFactory;
import io.camunda.client.jobhandling.JobHandlerFactory;
import io.camunda.client.jobhandling.JobWorkerManager;
import io.camunda.client.jobhandling.ManagedJobWorker;
import io.camunda.client.lifecycle.CamundaClientLifecycleAware;
import io.camunda.client.metrics.MetricsRecorder;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;
import io.camunda.connector.runtime.core.secret.SecretFilterFactory;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.metrics.ConnectorOutboundMetrics;
import io.camunda.connector.runtime.outbound.job.SpringConnectorJobHandler;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutboundConnectorManager implements CamundaClientLifecycleAware {

  private static final Logger LOG = LoggerFactory.getLogger(OutboundConnectorManager.class);
  private final JobWorkerManager jobWorkerManager;
  private final OutboundConnectorFactory connectorFactory;
  private final JobCallbackCommandWrapperFactory jobCallbackCommandWrapperFactory;
  private final SecretProviderAggregator secretProviderAggregator;
  private final ValidationProvider validationProvider;
  private final Map<String, DocumentFactory> documentFactoriesByPhysicalTenantId;
  private final ObjectMapper objectMapper;
  private final MetricsRecorder metricsRecorder;
  private final Map<String, SecretFilterFactory> secretFilterFactoriesByPhysicalTenantId;
  private final MeterRegistry meterRegistry;

  /**
   * One {@link OutboundConnectorFunction} instance per (physical tenant, connector type) pair —
   * connectors are re-created per physical tenant for isolation, rather than sharing the single
   * globally-cached instance {@link OutboundConnectorFactory#getInstance(String)} would return.
   * Entries persist for the process lifetime and are reused across an {@code onStop}+{@code
   * onStart} cycle for the same tenant (e.g. a client reconnect) — connectors have no
   * close/lifecycle contract to invoke on teardown.
   */
  private final Map<InstanceCacheKey, OutboundConnectorFunction>
      connectorInstancesByPhysicalTenant = new ConcurrentHashMap<>();

  private record InstanceCacheKey(String physicalTenantId, String connectorType) {}

  public OutboundConnectorManager(
      JobWorkerManager jobWorkerManager,
      OutboundConnectorFactory connectorFactory,
      JobCallbackCommandWrapperFactory jobCallbackCommandWrapperFactory,
      SecretProviderAggregator secretProviderAggregator,
      ValidationProvider validationProvider,
      Map<String, DocumentFactory> documentFactoriesByPhysicalTenantId,
      ObjectMapper objectMapper,
      MetricsRecorder metricsRecorder,
      Map<String, SecretFilterFactory> secretFilterFactoriesByPhysicalTenantId,
      MeterRegistry meterRegistry) {
    this.jobWorkerManager = jobWorkerManager;
    this.connectorFactory = connectorFactory;
    this.jobCallbackCommandWrapperFactory = jobCallbackCommandWrapperFactory;
    this.secretProviderAggregator = secretProviderAggregator;
    this.validationProvider = validationProvider;
    this.documentFactoriesByPhysicalTenantId = documentFactoriesByPhysicalTenantId;
    this.objectMapper = objectMapper;
    this.metricsRecorder = metricsRecorder;
    this.secretFilterFactoriesByPhysicalTenantId = secretFilterFactoriesByPhysicalTenantId;
    this.meterRegistry = meterRegistry;
  }

  /**
   * Required by {@link CamundaClientLifecycleAware}; in practice unreachable in a Spring context,
   * since {@code CamundaClientEventListener} always invokes the 2-arg {@link
   * #onStart(CamundaClient, String)} overload instead. Kept as a thin single-client-compatible
   * delegation for interface compliance and direct/manual invocation (e.g. tests).
   */
  @Override
  public void onStart(final CamundaClient client) {
    onStart(client, "default");
  }

  /** See {@link #onStart(CamundaClient)}. */
  @Override
  public void onStop(CamundaClient client) {
    onStop(client, "default");
  }

  @Override
  public void onStart(final CamundaClient client, final String clientName) {
    var physicalTenantId = resolvePhysicalTenantId(client, clientName);
    var documentFactory = documentFactoriesByPhysicalTenantId.get(physicalTenantId);
    var secretFilterFactory = secretFilterFactoriesByPhysicalTenantId.get(physicalTenantId);
    if (documentFactory == null || secretFilterFactory == null) {
      throw new IllegalStateException(
          "No DocumentFactory/SecretFilterFactory configured for physical tenant '"
              + physicalTenantId
              + "'");
    }
    // Currently, existing Spring beans have a higher priority
    // One result is that you will not disable Spring Bean Connectors by providing environment
    // variables for a specific connector
    Set<OutboundConnectorConfiguration> outboundConnectors =
        new TreeSet<>(new OutboundConnectorConfigurationComparator());
    outboundConnectors.addAll(connectorFactory.getActiveConfigurations());
    outboundConnectors.forEach(
        connector ->
            openWorkerForOutboundConnector(
                client, physicalTenantId, documentFactory, secretFilterFactory, connector));
  }

  @Override
  public void onStop(final CamundaClient client, final String clientName) {
    // client-scoped: tearing down one physical tenant's client must not close every other
    // physical tenant's job workers registered by this manager
    jobWorkerManager.closeJobWorkers(this, client);
  }

  /**
   * Resolves the physical tenant ID for a job-worker-opening client: the explicitly configured
   * {@code physical-tenant-id} if present, otherwise the Spring client bean name ({@code
   * clientName}) itself. Falls back to {@code clientName} if the configuration cannot be read at
   * all — some test doubles defer real initialization until a test container is ready and throw if
   * queried too early. Unlike the inbound side, no {@code CamundaClientRegistry} lookup is needed
   * here: {@code onStart(client, clientName)} is already handed the real client instance directly.
   */
  private static String resolvePhysicalTenantId(CamundaClient client, String clientName) {
    try {
      var physicalTenantId = client.getConfiguration().getPhysicalTenantId();
      return physicalTenantId != null ? physicalTenantId : clientName;
    } catch (RuntimeException e) {
      return clientName;
    }
  }

  private void openWorkerForOutboundConnector(
      CamundaClient client,
      String physicalTenantId,
      DocumentFactory documentFactory,
      SecretFilterFactory secretFilterFactory,
      OutboundConnectorConfiguration connector) {
    JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setName(new FromAnnotation<>(connector.name()));
    jobWorkerValue.setType(new FromAnnotation<>(connector.type()));
    jobWorkerValue.setFetchVariables(
        Arrays.stream(connector.inputVariables())
            .map(FromAnnotation::new)
            .map(fa -> (SourceAware<String>) fa)
            .toList());

    if (connector.timeout() != null) {
      jobWorkerValue.setTimeout(new FromAnnotation<>(Duration.ofMillis(connector.timeout())));
    }

    OutboundConnectorFunction connectorFunction =
        connectorInstancesByPhysicalTenant.computeIfAbsent(
            new InstanceCacheKey(physicalTenantId, connector.type()),
            key -> connector.instanceSupplier().get());
    LOG.trace(
        "Opening worker for connector {} on physical tenant '{}'",
        connector.name(),
        physicalTenantId);

    JobHandlerFactory jobHandlerFactory =
        ctx ->
            new SpringConnectorJobHandler(
                new ConnectorOutboundMetrics(metricsRecorder, meterRegistry),
                jobCallbackCommandWrapperFactory,
                secretProviderAggregator,
                validationProvider,
                documentFactory,
                objectMapper,
                connectorFunction,
                secretFilterFactory);
    jobWorkerManager.createJobWorker(
        client, new ManagedJobWorker(jobWorkerValue, jobHandlerFactory), this);
  }
}
