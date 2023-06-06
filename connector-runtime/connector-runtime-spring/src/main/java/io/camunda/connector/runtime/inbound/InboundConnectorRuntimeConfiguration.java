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
package io.camunda.connector.runtime.inbound;

import io.camunda.connector.runtime.core.feel.FeelEngineWrapper;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.inbound.importer.ProcessDefinitionImportConfiguration;
import io.camunda.connector.runtime.inbound.lifecycle.InboundConnectorLifecycleConfiguration;
import io.camunda.connector.runtime.inbound.lifecycle.MeteredInboundCorrelationHandler;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.spring.client.metrics.MetricsRecorder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({InboundConnectorLifecycleConfiguration.class, ProcessDefinitionImportConfiguration.class})
public class InboundConnectorRuntimeConfiguration {

  @Bean
  public InboundCorrelationHandler inboundCorrelationHandler(
      final ZeebeClient zeebeClient,
      final FeelEngineWrapper feelEngine,
      final MetricsRecorder metricsRecorder) {
    return new MeteredInboundCorrelationHandler(zeebeClient, feelEngine, metricsRecorder);
  }
}
