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

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MeteredSecretProviderAggregatorTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

  @Test
  void countsOneResolutionPerResolvedSecret() {
    var aggregator = aggregatorHolding(Map.of("TOKEN", "tok-1"));

    aggregator.getSecret("TOKEN", context("engine-a"));
    aggregator.getSecret("TOKEN", context("engine-a"));

    assertThat(count("engine-a")).isEqualTo(2);
  }

  @Test
  void countsPerOrchestrationCluster() {
    var aggregator = aggregatorHolding(Map.of("TOKEN", "tok-1"));

    aggregator.getSecret("TOKEN", context("engine-a"));
    aggregator.getSecret("TOKEN", context("engine-b"));

    assertThat(count("engine-a")).isEqualTo(1);
    assertThat(count("engine-b")).isEqualTo(1);
  }

  @Test
  void countsNothingWhenNoSecretResolves() {
    var aggregator = aggregatorHolding(Map.of());

    assertThat(aggregator.getSecret("TOKEN", context("engine-a"))).isNull();
    assertThat(registry.find(ConnectorMetrics.Secrets.METRIC_NAME_LEGACY_RESOLUTIONS).counters())
        .isEmpty();
  }

  @Test
  void stillCountsWhenTheLookupCarriesNoCluster() {
    var aggregator = aggregatorHolding(Map.of("TOKEN", "tok-1"));

    aggregator.getSecret("TOKEN", null);

    assertThat(count(ConnectorMetrics.DEFAULT_PHYSICAL_TENANT_ID)).isEqualTo(1);
  }

  private double count(String physicalTenantId) {
    return registry
        .get(ConnectorMetrics.Secrets.METRIC_NAME_LEGACY_RESOLUTIONS)
        .tag(ConnectorMetrics.Tag.PHYSICAL_TENANT_ID, physicalTenantId)
        .counter()
        .count();
  }

  private MeteredSecretProviderAggregator aggregatorHolding(Map<String, String> values) {
    SecretProvider provider =
        new SecretProvider() {
          @Override
          public String getSecret(String name, SecretContext context) {
            return values.get(name);
          }
        };
    return new MeteredSecretProviderAggregator(List.of(provider), registry);
  }

  private static SecretContext context(String physicalTenantId) {
    return new SecretContext("tenant", "process", physicalTenantId);
  }
}
