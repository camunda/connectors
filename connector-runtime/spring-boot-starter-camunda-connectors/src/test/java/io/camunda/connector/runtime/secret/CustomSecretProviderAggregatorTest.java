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

import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.secret.CustomSecretProviderAggregatorTest.CustomSecretProviderAggregator;
import io.camunda.connector.runtime.secret.providers.BarSpringSecretProvider;
import io.camunda.connector.runtime.secret.providers.FooSpringSecretProvider;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;

@SpringBootTest(
    classes = {
      TestConnectorRuntimeApplication.class,
      FooSpringSecretProvider.class,
      BarSpringSecretProvider.class,
      CustomSecretProviderAggregator.class
    })
public class CustomSecretProviderAggregatorTest {

  @Autowired SecretProviderAggregator secretProviderAggregator;

  @Test
  void customAggregatorCanOverrideDefault() {
    // given 2 secret providers defined in the spring context
    // and a custom secret provider aggregator
    Assertions.assertThat(secretProviderAggregator.getSecret("FOO")).isEqualTo("CUSTOM");
    Assertions.assertThat(secretProviderAggregator.getSecret("BAR")).isEqualTo("CUSTOM");
  }

  @Component
  public static class CustomSecretProviderAggregator extends SecretProviderAggregator {

    @Override
    public String getSecret(String secretName) {
      return "CUSTOM";
    }
  }
}
