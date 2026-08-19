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
package io.camunda.connector.runtime.metrics;

import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts how often a legacy secret reference actually resolves, per orchestration cluster.
 *
 * <p>Nothing else measures this, and whether the legacy syntax can be retired is a question about
 * how much it is still used. The counter is incremented only when a lookup produces a value, so it
 * counts resolutions rather than attempts.
 */
public class MeteredSecretProviderAggregator extends SecretProviderAggregator {

  private final MeterRegistry meterRegistry;
  private final Map<String, Counter> countersByPhysicalTenantId = new ConcurrentHashMap<>();

  public MeteredSecretProviderAggregator(
      List<SecretProvider> secretProviders, MeterRegistry meterRegistry) {
    super(secretProviders);
    this.meterRegistry = meterRegistry;
  }

  @Override
  public String getSecret(String secretName, SecretContext context) {
    String value = super.getSecret(secretName, context);
    if (value != null) {
      counterFor(context).increment();
    }
    return value;
  }

  private Counter counterFor(SecretContext context) {
    String physicalTenantId =
        context == null || context.physicalTenantId() == null
            ? ConnectorMetrics.DEFAULT_PHYSICAL_TENANT_ID
            : context.physicalTenantId();
    return countersByPhysicalTenantId.computeIfAbsent(
        physicalTenantId,
        tenant ->
            Counter.builder(ConnectorMetrics.Secrets.METRIC_NAME_LEGACY_RESOLUTIONS)
                .tag(ConnectorMetrics.Tag.PHYSICAL_TENANT_ID, tenant)
                .register(meterRegistry));
  }
}
