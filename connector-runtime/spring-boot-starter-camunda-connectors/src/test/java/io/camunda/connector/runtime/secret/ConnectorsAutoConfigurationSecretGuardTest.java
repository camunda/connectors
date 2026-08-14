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
package io.camunda.connector.runtime.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.runtime.ConnectorsAutoConfiguration;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;

/**
 * Unit tests for {@link ConnectorsAutoConfiguration#secretProviderAggregatorLegacySwitchGuard}: a
 * custom {@link SecretProviderAggregator} bean bypasses {@code springSecretProviderAggregator}
 * (it's only present via {@code @ConditionalOnMissingBean}), so the legacy off switch cannot be
 * enforced through that bean once a custom one exists. This guard is the unconditional bean that
 * catches the combination instead of silently ignoring it - see {@link LegacySecretsDisabledTest}
 * and {@link CustomSecretProviderAggregatorTest} for the two halves of this behavior in isolation,
 * exercised through a real Spring context.
 */
class ConnectorsAutoConfigurationSecretGuardTest {

  private final ConnectorsAutoConfiguration autoConfiguration =
      new ConnectorsAutoConfiguration(mock(ObjectProvider.class));

  @Test
  void legacyEnabled_neverInspectsTheAggregatorBeans() {
    ApplicationContext applicationContext = mock(ApplicationContext.class);

    assertThat(
            autoConfiguration.secretProviderAggregatorLegacySwitchGuard(applicationContext, true))
        .isNotNull();
  }

  @Test
  void legacyDisabled_onlyTheDefaultAggregatorBean_doesNotThrow() {
    ApplicationContext applicationContext = mock(ApplicationContext.class);
    when(applicationContext.getBeanNamesForType(SecretProviderAggregator.class))
        .thenReturn(new String[] {"springSecretProviderAggregator"});

    assertThat(
            autoConfiguration.secretProviderAggregatorLegacySwitchGuard(applicationContext, false))
        .isNotNull();
  }

  @Test
  void legacyDisabled_customAggregatorBeanPresent_throws() {
    ApplicationContext applicationContext = mock(ApplicationContext.class);
    when(applicationContext.getBeanNamesForType(SecretProviderAggregator.class))
        .thenReturn(new String[] {"myCustomAggregator"});

    assertThatThrownBy(
            () ->
                autoConfiguration.secretProviderAggregatorLegacySwitchGuard(
                    applicationContext, false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("camunda.connector.secret-resolver.legacy.enabled=false")
        .hasMessageContaining("myCustomAggregator");
  }
}
