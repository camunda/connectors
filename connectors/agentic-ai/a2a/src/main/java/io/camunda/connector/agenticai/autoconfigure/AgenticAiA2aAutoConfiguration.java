/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.autoconfigure;

import io.camunda.connector.agenticai.a2a.client.agentic.tool.configuration.A2aClientAgenticToolConfiguration;
import io.camunda.connector.agenticai.a2a.client.inbound.polling.configuration.A2aClientPollingConfiguration;
import io.camunda.connector.agenticai.a2a.client.inbound.webhook.configuration.A2aClientWebhookConfiguration;
import io.camunda.connector.agenticai.a2a.client.outbound.configuration.A2aClientOutboundConnectorConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnBooleanProperty(value = "camunda.connector.agenticai.enabled", matchIfMissing = true)
@Import({
  A2aClientOutboundConnectorConfiguration.class,
  A2aClientAgenticToolConfiguration.class,
  A2aClientPollingConfiguration.class,
  A2aClientWebhookConfiguration.class
})
public class AgenticAiA2aAutoConfiguration {}
