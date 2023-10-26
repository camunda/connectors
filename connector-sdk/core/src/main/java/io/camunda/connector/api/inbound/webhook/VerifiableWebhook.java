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

import java.util.Map;

public interface VerifiableWebhook {
  default WebhookHttpVerificationResult verify(WebhookProcessingPayload payload) throws Exception {
    return null;
  }

  record WebhookHttpVerificationResult(Object body, Map<String, String> headers, int statusCode) {
    public WebhookHttpVerificationResult() {
      this(Map.of(), null, 200);
    }

    public WebhookHttpVerificationResult(Object body) {
      this(body, null, 200);
    }

    public WebhookHttpVerificationResult(Object body, int statusCode) {
      this(body, null, statusCode);
    }

    public WebhookHttpVerificationResult(Object body, Map<String, String> headers) {
      this(body, headers, 200);
    }
  }
}
