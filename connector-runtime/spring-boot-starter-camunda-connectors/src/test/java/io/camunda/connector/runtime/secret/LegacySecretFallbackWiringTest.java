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

import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.core.secret.CentralStoreSecretProvider;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.secret.providers.FooSpringSecretProvider;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

class LegacySecretFallbackWiringTest {

  @Nested
  @SpringBootTest(
      classes = {TestConnectorRuntimeApplication.class, FooSpringSecretProvider.class},
      properties = {
        "camunda.connector.secret-resolver.legacy.mode=FALLBACK",
        "camunda.connector.secret-resolver.secret-filter.mode=STRICT"
      })
  class WhenFallbackIsChosen {

    @Autowired SecretProviderAggregator secretProviderAggregator;

    @Test
    void readsTheClusterAfterEveryConfiguredProvider() {
      var providers = secretProviderAggregator.getSecretProviders();

      assertThat(providers).hasAtLeastOneElementOfType(CentralStoreSecretProvider.class);
      assertThat(providers.getLast()).isInstanceOf(CentralStoreSecretProvider.class);
    }

    @Test
    void stillPrefersAConfiguredProvider() {
      assertThat(secretProviderAggregator.getSecret("FOO", null)).isEqualTo("FOO");
    }
  }

  @Nested
  @SpringBootTest(
      classes = {TestConnectorRuntimeApplication.class, FooSpringSecretProvider.class},
      properties = {"camunda.connector.secret-resolver.legacy.mode=FALLBACK"})
  class WhenFallbackIsChosenWithNoExplicitFilterMode {

    @Autowired SecretProviderAggregator secretProviderAggregator;

    @Test
    void startsBecauseTheSecretFilterDefaultsToStrict() {
      // The guard bean and the outbound secret filter each read their own @Value default for
      // camunda.connector.secret-resolver.secret-filter.mode independently; the app only starts
      // here if both resolve to STRICT.
      assertThat(secretProviderAggregator.getSecretProviders())
          .hasAtLeastOneElementOfType(CentralStoreSecretProvider.class);
    }
  }

  @Nested
  @SpringBootTest(classes = {TestConnectorRuntimeApplication.class, FooSpringSecretProvider.class})
  class WhenNothingIsConfigured {

    @Autowired SecretProviderAggregator secretProviderAggregator;

    @Test
    void neverReadsTheClusterImplicitly() {
      // A provider reading the environment is registered by default, so "no provider configured"
      // is never actually the case; the fallback has to be chosen.
      assertThat(secretProviderAggregator.getSecretProviders())
          .noneMatch(CentralStoreSecretProvider.class::isInstance);
    }
  }
}
