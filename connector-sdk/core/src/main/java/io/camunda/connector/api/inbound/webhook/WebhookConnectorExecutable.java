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
package io.camunda.connector.api.inbound.webhook;

import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;

/**
 * Base interface for inbound webhook connectors. The environment-specific Connector Runtime that
 * supports HTTP webhooks uses this interface to control the execution of inbound webhook
 * Connectors.
 */
public interface WebhookConnectorExecutable extends InboundConnectorExecutable {

  /**
   * Entry-point method whenever webhook was triggered. The purpose of the method is to perform
   * webhook-specific logic, such as replacing secrets, validating security mechanisms (e.g., HMAC,
   * digital signatures, challenges), or other checks that are not possible to cover with
   * <b>Activation condition</b>.
   *
   * @param payload Webhook request data, such as body, or headers. See {@link
   *     WebhookProcessingPayload}.
   * @return Processed webhook request data. See {@link WebhookResult}.
   */
  WebhookResult triggerWebhook(WebhookProcessingPayload payload) throws Exception;

  /**
   * @see InboundConnectorExecutable#activate(InboundConnectorContext)
   */
  @Override
  default void activate(InboundConnectorContext context) throws Exception {}

  /**
   * @see InboundConnectorExecutable#deactivate()
   */
  @Override
  default void deactivate() throws Exception {}
}
