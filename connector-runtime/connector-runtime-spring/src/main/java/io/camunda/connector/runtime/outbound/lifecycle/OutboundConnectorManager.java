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
import io.camunda.client.api.worker.JobHandler;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.metrics.ConnectorsOutboundMetrics;
import io.camunda.connector.runtime.outbound.jobhandling.SpringConnectorJobHandler;
import io.camunda.document.factory.DocumentFactory;
import io.camunda.spring.client.annotation.value.JobWorkerValue;
import io.camunda.spring.client.jobhandling.CommandExceptionHandlingStrategy;
import io.camunda.spring.client.jobhandling.JobWorkerManager;
import io.camunda.spring.client.metrics.DefaultNoopMetricsRecorder;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutboundConnectorManager {

  private static final Logger LOG = LoggerFactory.getLogger(OutboundConnectorManager.class);
  private final JobWorkerManager jobWorkerManager;
  private final OutboundConnectorFactory connectorFactory;
  private final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy;
  private final SecretProviderAggregator secretProviderAggregator;
  private final ValidationProvider validationProvider;
  private final ObjectMapper objectMapper;
  private final DocumentFactory documentFactory;
  private final ConnectorsOutboundMetrics outboundMetrics;

  public OutboundConnectorManager(
      JobWorkerManager jobWorkerManager,
      OutboundConnectorFactory connectorFactory,
      CommandExceptionHandlingStrategy commandExceptionHandlingStrategy,
      SecretProviderAggregator secretProviderAggregator,
      ValidationProvider validationProvider,
      DocumentFactory documentFactory,
      ObjectMapper objectMapper,
      ConnectorsOutboundMetrics outboundMetrics) {
    this.jobWorkerManager = jobWorkerManager;
    this.connectorFactory = connectorFactory;
    this.commandExceptionHandlingStrategy = commandExceptionHandlingStrategy;
    this.secretProviderAggregator = secretProviderAggregator;
    this.validationProvider = validationProvider;
    this.documentFactory = documentFactory;
    this.objectMapper = objectMapper;
    this.outboundMetrics = outboundMetrics;
  }

  public void start(final CamundaClient client) {
    // Currently, existing Spring beans have a higher priority
    // One result is that you will not disable Spring Bean Connectors by providing environment
    // variables for a specific connector
    Set<OutboundConnectorConfiguration> outboundConnectors =
        new TreeSet<>(new OutboundConnectorConfigurationComparator());

    outboundConnectors.addAll(connectorFactory.getConfigurations());
    outboundConnectors.forEach(connector -> openWorkerForOutboundConnector(client, connector));
  }

  public void stop() {
    jobWorkerManager.closeAllOpenWorkers();
  }

  private void openWorkerForOutboundConnector(
      CamundaClient client, OutboundConnectorConfiguration connector) {
    JobWorkerValue zeebeWorkerValue = new JobWorkerValue();
    zeebeWorkerValue.setName(connector.name());
    zeebeWorkerValue.setType(connector.type());
    zeebeWorkerValue.setFetchVariables(Arrays.asList(connector.inputVariables()));
    if (connector.timeout() != null) {
      zeebeWorkerValue.setTimeout(Duration.ofMillis(connector.timeout()));
    }
    zeebeWorkerValue.setAutoComplete(true);

    OutboundConnectorFunction connectorFunction = connectorFactory.getInstance(connector.type());
    LOG.trace("Opening worker for connector {}", connector.name());

    JobHandler connectorJobHandler =
        new SpringConnectorJobHandler(
            outboundMetrics,
            commandExceptionHandlingStrategy,
            secretProviderAggregator,
            validationProvider,
            documentFactory,
            objectMapper,
            connectorFunction,
            new DefaultNoopMetricsRecorder());

    jobWorkerManager.openWorker(client, zeebeWorkerValue, connectorJobHandler);
  }
}
