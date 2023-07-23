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
package io.camunda.connector.runtime.app;

import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.webhook.MappedHttpRequest;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import java.util.Map;

@InboundConnector(name = "TEST_WEBHOOK", type = "io.camunda:test-webhook:1")
public class TestWebhookConnector implements WebhookConnectorExecutable {

  @Override
  public WebhookResult triggerWebhook(WebhookProcessingPayload webhookProcessingPayload)
      throws Exception {
    return new WebhookResult() {

      @Override
      public MappedHttpRequest request() {
        return new MappedHttpRequest(
            Map.of("bodyKey", "bodyVal"), Map.of("X-Header", "XValue"), Map.of("param1", "value1"));
      }

      @Override
      public Map<String, Object> connectorData() {
        return Map.of("ConnectorDataKey1", "ConnectorDataValue");
      }
    };
  }

  @Override
  public void activate(InboundConnectorContext context) throws Exception {}

  @Override
  public void deactivate() throws Exception {}
}
