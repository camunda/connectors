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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.connector.runtime.ConnectorsAutoConfiguration;
import io.camunda.connector.runtime.core.secret.LegacySecretMode;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.outbound.job.ConfigurableSecretFilterFactory.SecretFilterMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;

/**
 * A custom {@link SecretProviderAggregator} bean replaces the one that applies the legacy mode,
 * because that one exists only through {@code @ConditionalOnMissingBean}. Without this guard the
 * setting would be silently ignored.
 */
@SuppressWarnings("unchecked")
class LegacySecretModeGuardTest {

  private final ConnectorsAutoConfiguration autoConfiguration =
      new ConnectorsAutoConfiguration(mock(ObjectProvider.class));

  @ParameterizedTest
  @ValueSource(strings = {"OFF", "off", " on ", "FALLBACK", "Fallback"})
  void readsTheModeWhateverItsCapitalisation(String configured) {
    assertThat(LegacySecretMode.parse(configured))
        .isEqualTo(LegacySecretMode.valueOf(configured.trim().toUpperCase(java.util.Locale.ROOT)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "yes", "true", "DISABLED"})
  void refusesAModeItCannotRead(String configured) {
    // Spring's own conversion turns an empty value into null rather than failing, which for this
    // setting would mean silently resolving legacy secrets after an operator tried to switch them
    // off.
    assertThatThrownBy(() -> LegacySecretMode.parse(configured))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(LegacySecretMode.PROPERTY);
  }

  @Test
  void refusesAModeThatIsNotSetAtAll() {
    assertThatThrownBy(() -> LegacySecretMode.parse(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void doesNotInspectTheAggregatorBeansWhenLegacyResolutionIsOn() {
    ApplicationContext applicationContext = mock(ApplicationContext.class);

    assertThat(
            autoConfiguration.checkSecretProviderAggregatorLegacySwitch(
                applicationContext, LegacySecretMode.ON))
        .isNotNull();
    verifyNoInteractions(applicationContext);
  }

  @Test
  void acceptsTheRuntimesOwnAggregator() {
    ApplicationContext applicationContext = mock(ApplicationContext.class);
    when(applicationContext.getBeanNamesForType(SecretProviderAggregator.class))
        .thenReturn(new String[] {"springSecretProviderAggregator"});

    assertThat(
            autoConfiguration.checkSecretProviderAggregatorLegacySwitch(
                applicationContext, LegacySecretMode.OFF))
        .isNotNull();
  }

  @ParameterizedTest
  @EnumSource(
      value = SecretFilterMode.class,
      names = {"DISABLED", "LAX"})
  void refusesToStartOnFallbackWithoutAStrictSecretFilter(SecretFilterMode secretFilterMode) {
    assertThatThrownBy(
            () ->
                autoConfiguration.checkLegacyFallbackSecretFilter(
                    LegacySecretMode.FALLBACK, secretFilterMode))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("camunda.connector.secret-resolver.legacy.mode=FALLBACK")
        .hasMessageContaining("secret-filter.mode=STRICT");
  }

  @Test
  void startsOnFallbackWithAStrictSecretFilter() {
    assertThat(
            autoConfiguration.checkLegacyFallbackSecretFilter(
                LegacySecretMode.FALLBACK, SecretFilterMode.STRICT))
        .isNotNull();
  }

  @ParameterizedTest
  @EnumSource(SecretFilterMode.class)
  void leavesTheSecretFilterAloneWhenTheFallbackIsNotInUse(SecretFilterMode secretFilterMode) {
    assertThat(
            autoConfiguration.checkLegacyFallbackSecretFilter(
                LegacySecretMode.ON, secretFilterMode))
        .isNotNull();
    assertThat(
            autoConfiguration.checkLegacyFallbackSecretFilter(
                LegacySecretMode.OFF, secretFilterMode))
        .isNotNull();
  }

  @Test
  void refusesToStartWhenACustomAggregatorWouldBypassTheSetting() {
    ApplicationContext applicationContext = mock(ApplicationContext.class);
    when(applicationContext.getBeanNamesForType(SecretProviderAggregator.class))
        .thenReturn(new String[] {"myCustomAggregator"});

    assertThatThrownBy(
            () ->
                autoConfiguration.checkSecretProviderAggregatorLegacySwitch(
                    applicationContext, LegacySecretMode.OFF))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("camunda.connector.secret-resolver.legacy.mode=OFF")
        .hasMessageContaining("myCustomAggregator");
  }
}
