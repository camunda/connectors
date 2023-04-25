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
package io.camunda.connector.runtime.inbound.lifecycle;

import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.impl.ConnectorUtil;
import io.camunda.connector.runtime.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.runtime.inbound.webhook.WebhookConnectorRegistry;
import io.camunda.connector.runtime.util.inbound.DefaultInboundConnectorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * {@link io.camunda.connector.runtime.util.inbound.InboundConnectorFactory} implementation that
 * supports discovering Connectors defined as Spring beans. Connectors that are defined as Spring
 * beans are created by Spring.
 */
@Component
public class SpringInboundConnectorFactory extends DefaultInboundConnectorFactory {

  private static final Logger LOG = LoggerFactory.getLogger(SpringInboundConnectorFactory.class);
  private final WebhookConnectorRegistry webhookConnectorRegistry;

  public SpringInboundConnectorFactory(
      @Autowired(required = false) WebhookConnectorRegistry webhookConnectorRegistry) {
    super(); // run default connector discovery mechanisms
    this.webhookConnectorRegistry = webhookConnectorRegistry;
    // register webhook if it's not disabled
    if (webhookConnectorRegistry != null) {
      LOG.debug("Registering webhook connector configuration");
      registerConfiguration(
          ConnectorUtil.getRequiredInboundConnectorConfiguration(WebhookConnectorExecutable.class));
    }
  }

  @Override
  public InboundConnectorExecutable getInstance(String type) {
    if (webhookConnectorRegistry != null && WebhookConnectorRegistry.TYPE_WEBHOOK.equals(type)) {
      return new WebhookConnectorExecutable(webhookConnectorRegistry);
    }
    return super.getInstance(type);
  }
}
