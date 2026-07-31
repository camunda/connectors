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
package io.camunda.connector.runtime.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.feel.CamundaClientFeelExpressionEvaluator;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Verifies that configuration (credential) validation wires one FEEL evaluator per configured
 * physical tenant when several {@code camunda.clients.*} are configured. A stored configuration
 * lives on exactly one orchestration cluster, so resolving its {@code credentialRef} through a
 * single cluster-wide evaluator would read the wrong engine's {@code camunda.vars.env.*}.
 *
 * <p>The map is fetched by explicit bean name via {@link ApplicationContext} rather than
 * {@code @Autowired} field injection: Spring's dependency resolution special-cases any {@code
 * Map<String, X>} autowiring point by collecting all beans of type {@code X} by name, which would
 * silently resolve to the scalar {@code @Primary FeelExpressionEvaluator} bean instead of the real
 * per-physical-tenant map.
 */
@SpringBootTest(
    classes = TestConnectorRuntimeApplication.class,
    properties = {
      "camunda.clients.engine-a.mode=self-managed",
      "camunda.clients.engine-a.grpc-address=http://engine-a.internal:26500",
      "camunda.clients.engine-a.physical-tenant-id=tenanta",
      // marks engine-a as @Primary so the pre-existing single-CamundaClient autowiring points
      // elsewhere still resolve unambiguously with two clients configured
      "camunda.clients.engine-a.primary=true",
      "camunda.clients.engine-b.mode=self-managed",
      "camunda.clients.engine-b.grpc-address=http://engine-b.internal:26500",
      "camunda.clients.engine-b.physical-tenant-id=tenantb",
      "camunda.connector.polling.enabled=false",
      "camunda.connector.webhook.enabled=false"
    })
class MultiClientConfigurationValidationWiringTest {

  @Autowired private ApplicationContext applicationContext;

  @SuppressWarnings("unchecked")
  private Map<String, Object> evaluatorsByPhysicalTenantId() {
    return (Map<String, Object>)
        applicationContext.getBean("feelExpressionEvaluatorsByPhysicalTenantId", Map.class);
  }

  @Test
  void wiresOneEvaluatorPerConfiguredPhysicalTenant() {
    assertThat(evaluatorsByPhysicalTenantId()).containsOnlyKeys("tenanta", "tenantb");
  }

  @Test
  void everyEvaluatorIsClusterBackedAndDistinct() {
    var evaluators = evaluatorsByPhysicalTenantId();

    // With several clients the scalar @Primary evaluator is deliberately ignored: reusing it for
    // every tenant is exactly the single-engine bug this map exists to prevent.
    assertThat(evaluators.values())
        .allSatisfy(e -> assertThat(e).isInstanceOf(CamundaClientFeelExpressionEvaluator.class));
    assertThat(evaluators.get("tenanta")).isNotSameAs(evaluators.get("tenantb"));
  }
}
