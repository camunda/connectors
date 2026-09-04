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

import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.runtime.ConnectorsAutoConfiguration;
import io.camunda.connector.runtime.core.secret.LegacySecretMode;
import io.camunda.connector.runtime.core.secret.LegacySecretsDisabledProvider;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.metrics.MeteredSecretProviderAggregator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;

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
  void doesNotInspectTheAggregatorWhenLegacyResolutionIsOn() {
    SecretProviderAggregator secretProviderAggregator = mock(SecretProviderAggregator.class);

    assertThat(
            autoConfiguration.checkSecretProviderAggregatorLegacySwitch(
                secretProviderAggregator, LegacySecretMode.ON))
        .isNotNull();
    verifyNoInteractions(secretProviderAggregator);
  }

  @Test
  void acceptsTheRuntimesOwnAggregator() {
    var secretProviderAggregator =
        new SecretProviderAggregator(List.of(new LegacySecretsDisabledProvider()));

    assertThat(
            autoConfiguration.checkSecretProviderAggregatorLegacySwitch(
                secretProviderAggregator, LegacySecretMode.OFF))
        .isNotNull();
  }

  @Test
  void refusesToStartWhenAnAggregatorHoldsTheDisabledProviderButResolvesAnyway() {
    // The provider list alone proves nothing: SecretProviderAggregator is not final and its lookup
    // methods are overridable — MeteredSecretProviderAggregator is itself an override — so a
    // subclass can carry exactly the list this guard wants and resolve values regardless.
    var bypassing =
        new SecretProviderAggregator(List.of(new LegacySecretsDisabledProvider())) {
          @Override
          public String getSecret(String secretName, SecretContext context) {
            return "resolved-anyway";
          }
        };

    assertThatThrownBy(
            () ->
                autoConfiguration.checkSecretProviderAggregatorLegacySwitch(
                    bypassing, LegacySecretMode.OFF))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("camunda.connector.secret-resolver.legacy.mode=OFF");
  }

  @Test
  void refusesToStartWhenOnlyTheBatchLookupResolves() {
    // fetchAll is what the outbound paths call, and a subclass may override only that one.
    var bypassing =
        new SecretProviderAggregator(List.of(new LegacySecretsDisabledProvider())) {
          @Override
          public List<String> fetchAll(List<String> secretNames, SecretContext context) {
            return List.of("resolved-anyway");
          }
        };

    assertThatThrownBy(
            () ->
                autoConfiguration.checkSecretProviderAggregatorLegacySwitch(
                    bypassing, LegacySecretMode.OFF))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void acceptsTheMeteredAggregatorThatTheRuntimeItselfInstalls() {
    // The runtime's own subclass has to keep passing, or metrics and the switch become exclusive.
    var metered =
        new MeteredSecretProviderAggregator(
            List.of(new LegacySecretsDisabledProvider()), new SimpleMeterRegistry());

    assertThat(
            autoConfiguration.checkSecretProviderAggregatorLegacySwitch(
                metered, LegacySecretMode.OFF))
        .isNotNull();
  }

  @Test
  void refusesToStartWhenACustomAggregatorWouldBypassTheSetting() {
    SecretProviderAggregator customAggregator =
        new SecretProviderAggregator(List.of(mock(SecretProvider.class)));

    assertThatThrownBy(
            () ->
                autoConfiguration.checkSecretProviderAggregatorLegacySwitch(
                    customAggregator, LegacySecretMode.OFF))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("camunda.connector.secret-resolver.legacy.mode=OFF");
  }
}
