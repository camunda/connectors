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
import io.camunda.connector.api.error.retry.ConnectorRetryException;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.outbound.jobhandling.SpringConnectorJobHandler;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.spring.client.annotation.value.ZeebeWorkerValue;
import io.camunda.zeebe.spring.client.jobhandling.CommandExceptionHandlingStrategy;
import io.camunda.zeebe.spring.client.jobhandling.JobWorkerManager;
import io.camunda.zeebe.spring.client.metrics.MetricsRecorder;
import java.util.Arrays;
import java.util.HashSet;
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
  private final MetricsRecorder metricsRecorder;

  public OutboundConnectorManager(
      JobWorkerManager jobWorkerManager,
      OutboundConnectorFactory connectorFactory,
      CommandExceptionHandlingStrategy commandExceptionHandlingStrategy,
      SecretProviderAggregator secretProviderAggregator,
      ValidationProvider validationProvider,
      ObjectMapper objectMapper,
      MetricsRecorder metricsRecorder) {
    this.jobWorkerManager = jobWorkerManager;
    this.connectorFactory = connectorFactory;
    this.commandExceptionHandlingStrategy = commandExceptionHandlingStrategy;
    this.secretProviderAggregator = secretProviderAggregator;
    this.validationProvider = validationProvider;
    this.objectMapper = objectMapper;
    this.metricsRecorder = metricsRecorder;
  }

  public void start(final ZeebeClient client) {
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
      ZeebeClient client, OutboundConnectorConfiguration connector) {
    ZeebeWorkerValue zeebeWorkerValue = new ZeebeWorkerValue();
    zeebeWorkerValue.setName(connector.name());
    zeebeWorkerValue.setType(connector.type());
    zeebeWorkerValue.setFetchVariables(addRetryContextInputVariable(connector.inputVariables()));
    zeebeWorkerValue.setTimeout(connector.timeout());
    zeebeWorkerValue.setAutoComplete(true);

    OutboundConnectorFunction connectorFunction = connectorFactory.getInstance(connector.type());
    LOG.trace("Opening worker for connector {}", connector.name());

    JobHandler connectorJobHandler =
        new SpringConnectorJobHandler(
            metricsRecorder,
            commandExceptionHandlingStrategy,
            secretProviderAggregator,
            validationProvider,
            objectMapper,
            connectorFunction,
            connector);

    jobWorkerManager.openWorker(client, zeebeWorkerValue, connectorJobHandler);
  }

  /**
   * Adds the retry context input variable to the list of input variables, if it is not already.
   * This is required so that we can retrieve the retry context (attempts by errorCode) from the job
   * when throwing a {@link ConnectorRetryException}. This method is idempotent, meaning that it
   * will not add the retry context input variable if it is already present in the list of input
   * variables.
   *
   * @param inputVariables the list of input variables
   * @return the array of input variables including the retry context input variable
   */
  private String[] addRetryContextInputVariable(String[] inputVariables) {
    // Don't add anything, as we want to fetch all variables if this is empty
    if (inputVariables == null || inputVariables.length == 0) {
      return inputVariables;
    }
    var variableSet = new HashSet<>(Arrays.asList(inputVariables));
    variableSet.add(ConnectorRetryException.RETRY_CONTEXT_INPUT_VARIABLE);
    return variableSet.toArray(new String[0]);
  }
}
