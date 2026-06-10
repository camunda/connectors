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

/**
 * Names of well-known webhook connector properties that the connector runtime needs to recognize,
 * e.g. to exclude them from connector deduplication. Webhook connector implementations should
 * reference these constants in their property definitions so that the property IDs and the runtime
 * behavior cannot drift apart.
 */
public final class WebhookPropertyNames {

  /**
   * The property holding the FEEL expression used to generate the HTTP response of the webhook
   * endpoint after a successful correlation.
   */
  public static final String RESPONSE_EXPRESSION = "responseExpression";

  /**
   * The legacy predecessor of {@link #RESPONSE_EXPRESSION} that only produced the response body.
   * Hidden from element templates since 8.6.0, but still supported at runtime.
   */
  public static final String RESPONSE_BODY_EXPRESSION = "responseBodyExpression";

  private WebhookPropertyNames() {}
}
