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
 * Runtime binding target for the element-scoped webhook response expressions.
 *
 * <p>The raw properties of the activated element are bound to this record per request (see {@link
 * InboundWebhookRestController}). The {@code inbound} wrapper mirrors the {@code
 * inbound.responseExpression} / {@code inbound.responseBodyExpression} property bindings used in
 * the element template, so the same raw model keys deserialize here.
 *
 * <p>This is a structural twin of the webhook connector's element-scoped template class: the
 * connector module owns the template definition, while the runtime (which builds the HTTP response)
 * owns this binding target, because {@code connector-runtime-spring} does not depend on the webhook
 * connector module.
 */
public record WebhookResponseExpressionProperties(WebhookResponseExpressions inbound) {

  public record WebhookResponseExpressions(
      Function<WebhookResultContext, WebhookHttpResponse> responseExpression,
      Function<WebhookResultContext, Object> responseBodyExpression) {}
}
