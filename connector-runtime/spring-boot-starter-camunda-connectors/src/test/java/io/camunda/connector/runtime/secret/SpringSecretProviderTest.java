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
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.secret.providers.BarSpringSecretProvider;
import io.camunda.connector.runtime.secret.providers.FooSpringSecretProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {
      TestConnectorRuntimeApplication.class,
      FooSpringSecretProvider.class,
      BarSpringSecretProvider.class
    },
    properties = {"camunda.connector.secretprovider.discovery.enabled=false"})
public class SpringSecretProviderTest {

  @Autowired SecretProviderAggregator secretProviderAggregator;

  @Test
  void secretProviderIsLoadedFromSpringContext() {
    // given 2 secret providers defined in the spring context
    // then the secret provider aggregator should be able to find them
    assertThat(secretProviderAggregator.getSecret("FOO")).isEqualTo("FOO");
    assertThat(secretProviderAggregator.getSecret("BAR")).isEqualTo("BAR");
    // and SPI secret provider should not be loaded
    assertThat(secretProviderAggregator.getSecret("SPI")).isNull();
  }
}
