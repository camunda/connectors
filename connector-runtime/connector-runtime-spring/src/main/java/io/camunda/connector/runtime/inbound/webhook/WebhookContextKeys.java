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

import java.util.Objects;

/**
 * Composes the {@link WebhookConnectorRegistry} lookup key used when physical-tenant/tenant path
 * scoping is enabled, so registration and lookup can never compute it differently. {@code public}
 * so the same composition can be reused wherever the effective webhook path/URL needs to be derived
 * outside this package (e.g. {@code ConnectorDataMapper}, which exposes it via the REST API
 * consumed by web-modeler to build the webhook URL shown to users).
 */
public final class WebhookContextKeys {

  private WebhookContextKeys() {}

  public static String compose(String physicalTenantId, String tenantId, String path) {
    Objects.requireNonNull(
        physicalTenantId, "physicalTenantId must not be null when path scoping is enabled");
    Objects.requireNonNull(tenantId, "tenantId must not be null when path scoping is enabled");
    return physicalTenantId + "/" + tenantId + "/" + path;
  }
}
