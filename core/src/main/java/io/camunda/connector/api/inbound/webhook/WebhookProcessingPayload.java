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

/** A wrapper object for the webhook request. */
public interface WebhookProcessingPayload {

  /**
   * @return HTTP Request method as {@link String}
   */
  String method();

  /**
   * @return HTTP Request headers as {@link Map}
   */
  Map<String, String> headers();

  /**
   * @return HTTP Request URL parameters as {@link Map}
   */
  Map<String, String> params();

  /**
   * <b>Note:</b> byte array is chosen because several security mechanisms, such as HMAC rely on
   * original data 'as-is', and not being modified or tampered.
   *
   * @return HTTP Request body as byte array.
   */
  byte[] rawBody();
}
