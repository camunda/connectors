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

import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

/**
 * A wrapper object that returns data processed by a webhook. Depending on webhook runtime
 * implementation, this data can be used in the BPMN process correlation. See {@link
 * io.camunda.connector.api.inbound.InboundConnectorContext#correlateWithResult(Object)}.
 */
public interface WebhookResult {

  /**
   * @return processed HTTP request wrapper
   */
  MappedHttpRequest request();

  /**
   * A function that produces the HTTP response to the webhook request, applied by the runtime after
   * correlation. The {@link WebhookResultContext} exposes the processed request and the correlation
   * result, so the function may resolve the response from the activated element (for example, an
   * element-scoped webhook response expression — see the webhook connector) or return a fixed
   * response computed at trigger time (see the Slack connector).
   *
   * <p>Note: the function is evaluated past the transaction boundary (the process instance has
   * already been created or the message published), so failures cannot undo the correlation.
   */
  default Function<WebhookResultContext, WebhookHttpResponse> response() {
    return null;
  }

  /**
   * @return additional connector data that can be computed during webhook {@link
   *     WebhookConnectorExecutable#triggerWebhook(WebhookProcessingPayload)} execution, and the
   *     implementer wants to preserve that data and use it during the {@link
   *     io.camunda.connector.api.inbound.InboundConnectorContext#correlateWithResult(Object)}
   *     phase.
   */
  default Map<String, Object> connectorData() {
    return Collections.emptyMap();
  }
}
