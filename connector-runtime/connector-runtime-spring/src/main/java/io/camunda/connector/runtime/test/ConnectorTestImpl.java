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
package io.camunda.connector.runtime.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.runtime.core.outbound.ConnectorJobHandler;
import io.camunda.connector.runtime.core.outbound.DefaultOutboundConnectorFactory;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorDiscovery;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.impl.ZeebeObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ConnectorTestImpl implements ConnectorTest {
  ZeebeClient zeebeClient;

  private final JobClient jobClient = new ConnectorTestJobClientImpl();

  ObjectMapper objectMapper;

  SecretProviderAggregator secretProviderAggregator;

  OutboundConnectorFactory connectorFactory =
      new DefaultOutboundConnectorFactory(OutboundConnectorDiscovery.loadConnectorConfigurations());

  public ConnectorTestImpl(
      ZeebeClient zeebeClient,
      ObjectMapper objectMapper,
      SecretProviderAggregator secretProviderAggregator) {
    this.secretProviderAggregator = secretProviderAggregator;
    this.zeebeClient = zeebeClient;
    this.objectMapper = objectMapper;
  }

  public void test(ConnectorTestRq req) throws Exception {
    var job = new ConnectorTestActivatedJobImpl(new ZeebeObjectMapper(), req);
    OutboundConnectorFunction connectorFunction = connectorFactory.getInstance(job.getType());
    var jobHandler =
        new ConnectorJobHandler(connectorFunction, secretProviderAggregator, e -> {}, objectMapper);
    jobHandler.handle(jobClient, job);
  }
}
