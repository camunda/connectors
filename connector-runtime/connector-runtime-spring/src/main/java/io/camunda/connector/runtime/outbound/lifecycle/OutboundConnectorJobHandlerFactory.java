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
import io.camunda.client.api.worker.JobHandler;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.metrics.ConnectorsOutboundMetrics;
import io.camunda.connector.runtime.outbound.jobhandling.SpringConnectorJobHandler;
import io.camunda.document.factory.DocumentFactory;
import io.camunda.spring.client.jobhandling.JobHandlerFactory;
import io.camunda.spring.client.jobhandling.JobHandlerFactoryContext;
import io.camunda.spring.client.metrics.DefaultNoopMetricsRecorder;

public class OutboundConnectorJobHandlerFactory implements JobHandlerFactory {
  private final ConnectorsOutboundMetrics outboundMetrics;
  private final SecretProviderAggregator secretProviderAggregator;
  private final ValidationProvider validationProvider;
  private final DocumentFactory documentFactory;
  private final ObjectMapper objectMapper;
  private final OutboundConnectorFunction connectorFunction;

  public OutboundConnectorJobHandlerFactory(
      ConnectorsOutboundMetrics outboundMetrics,
      SecretProviderAggregator secretProviderAggregator,
      ValidationProvider validationProvider,
      DocumentFactory documentFactory,
      ObjectMapper objectMapper,
      OutboundConnectorFunction connectorFunction) {
    this.outboundMetrics = outboundMetrics;
    this.secretProviderAggregator = secretProviderAggregator;
    this.validationProvider = validationProvider;
    this.documentFactory = documentFactory;
    this.objectMapper = objectMapper;
    this.connectorFunction = connectorFunction;
  }

  @Override
  public JobHandler getJobHandler(JobHandlerFactoryContext context) {
    return new SpringConnectorJobHandler(
        outboundMetrics,
        context.commandExceptionHandlingStrategy(),
        secretProviderAggregator,
        validationProvider,
        documentFactory,
        objectMapper,
        connectorFunction,
        new DefaultNoopMetricsRecorder());
  }
}
