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
package io.camunda.connector.runtime.util.secret;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.runtime.util.FooBarSecretProvider;
import io.camunda.connector.runtime.util.NoOpSecretProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

public class SecretProviderAggregatorTest {

  @Test
  public void secretProviderOverride() {
    // given
    List<SecretProvider> override = List.of(new NoOpSecretProvider());
    SecretProviderAggregator aggregator = new SecretProviderAggregator(override);

    // when
    var secret = aggregator.getSecret(FooBarSecretProvider.SECRET_NAME);

    // then SPI provider not used
    assertThat(aggregator.getSecretProviders()).containsExactlyElementsOf(override);
    assertThat(secret).isNull();
  }

  @Test
  public void spiProviderDiscovery() {
    // given
    SecretProviderAggregator aggregator = new SecretProviderAggregator();

    // when
    var secret = new SecretProviderAggregator().getSecret(FooBarSecretProvider.SECRET_NAME);

    // then
    assertThat(aggregator.getSecretProviders()).hasSize(2);
    assertThat(aggregator.getSecretProviders().get(1)).isInstanceOf(NoOpSecretProvider.class);
    assertThat(aggregator.getSecretProviders().get(0)).isInstanceOf(FooBarSecretProvider.class);
    assertThat(secret).isEqualTo(FooBarSecretProvider.SECRET_VALUE);
  }
}
