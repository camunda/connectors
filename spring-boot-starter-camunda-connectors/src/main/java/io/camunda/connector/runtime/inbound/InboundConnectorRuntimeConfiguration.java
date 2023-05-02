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

import io.camunda.connector.runtime.env.FeelConfiguration;
import io.camunda.connector.runtime.inbound.importer.ProcessDefinitionImportConfiguration;
import io.camunda.connector.runtime.inbound.lifecycle.InboundConnectorLifecycleConfiguration;
import io.camunda.connector.runtime.inbound.webhook.InboundWebhookConnectorConfiguration;
import io.camunda.connector.runtime.util.feel.FeelEngineWrapper;
import io.camunda.connector.runtime.util.inbound.correlation.InboundCorrelationHandler;
import io.camunda.zeebe.client.ZeebeClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnProperty(
    prefix = "camunda.connector.polling",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@Import({
  InboundConnectorLifecycleConfiguration.class,
  InboundWebhookConnectorConfiguration.class,
  ProcessDefinitionImportConfiguration.class,
  FeelConfiguration.class
})
public class InboundConnectorRuntimeConfiguration {

  @Bean
  public InboundCorrelationHandler inboundCorrelationHandler(
      final ZeebeClient zeebeClient, final FeelEngineWrapper feelEngine) {
    return new InboundCorrelationHandler(zeebeClient, feelEngine);
  }
}
