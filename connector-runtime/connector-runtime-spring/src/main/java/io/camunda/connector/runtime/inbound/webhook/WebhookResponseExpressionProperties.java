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
package io.camunda.connector.runtime.inbound.webhook;

import io.camunda.connector.api.inbound.webhook.WebhookHttpResponse;
import io.camunda.connector.api.inbound.webhook.WebhookResultContext;
import java.util.function.Function;

/**
 * Minimal binding target for the webhook response expressions of a single process element.
 *
 * <p>The webhook response expression is excluded from deduplication, so several elements may share
 * one executable while declaring different response expressions. After correlation, {@link
 * InboundWebhookRestController} binds the activated element's response expression into this holder
 * (reusing the same FEEL / secret machinery as regular property binding) and evaluates it, instead
 * of relying on the expression bound at activation time from an arbitrary group member.
 *
 * <p>The {@code inbound} wrapper mirrors the {@code inbound.} prefix that webhook properties carry
 * in the process model.
 */
public record WebhookResponseExpressionProperties(WebhookResponseExpressions inbound) {

  public record WebhookResponseExpressions(
      Function<WebhookResultContext, WebhookHttpResponse> responseExpression,
      Function<WebhookResultContext, Object> responseBodyExpression) {}
}
