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
import io.camunda.connector.feel.FeelExpressionEvaluator;
import io.camunda.connector.feel.LocalFeelExpressionEvaluator;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Verifies that configuration validation uses a cluster-backed FEEL evaluator even when the ambient
 * {@code FeelExpressionEvaluator} bean has been replaced by a local (embedded-engine) one.
 *
 * <p>{@code ConnectorsAutoConfiguration} declares that bean {@code @ConditionalOnMissingBean}, so a
 * deployment or test can substitute it. A local evaluator cannot reach {@code camunda.vars.env.*}
 * at all, so a stored configuration reference would fail at request time with nothing pointing back
 * at the substituted bean. Validation therefore builds its own evaluators from the {@code
 * CamundaClient} and ignores the ambient bean entirely — this test pins that.
 */
@SpringBootTest(
    classes = {
      TestConnectorRuntimeApplication.class,
      SingleClientConfigurationValidationWiringTest.LocalEvaluatorOverride.class
    },
    properties = {
      "camunda.connector.polling.enabled=false",
      "camunda.connector.webhook.enabled=false"
    })
class SingleClientConfigurationValidationWiringTest {

  @TestConfiguration
  static class LocalEvaluatorOverride {
    @Bean
    @Primary
    FeelExpressionEvaluator feelExpressionEvaluator() {
      return new LocalFeelExpressionEvaluator();
    }
  }

  @Autowired private ApplicationContext applicationContext;

  @Autowired private FeelExpressionEvaluator ambientEvaluator;

  @SuppressWarnings("unchecked")
  private Map<String, Object> evaluatorsByPhysicalTenantId() {
    return (Map<String, Object>)
        applicationContext.getBean("feelExpressionEvaluatorsByPhysicalTenantId", Map.class);
  }

  @Test
  void theAmbientBeanIsTheLocalOverride() {
    // Guards the premise: if this stops being the local evaluator, the assertion below proves
    // nothing.
    assertThat(ambientEvaluator).isInstanceOf(LocalFeelExpressionEvaluator.class);
  }

  @Test
  void validationStillGetsAClusterBackedEvaluator() {
    assertThat(evaluatorsByPhysicalTenantId())
        .hasSize(1)
        .allSatisfy(
            (physicalTenantId, evaluator) ->
                assertThat(evaluator).isInstanceOf(CamundaClientFeelExpressionEvaluator.class));
  }
}
