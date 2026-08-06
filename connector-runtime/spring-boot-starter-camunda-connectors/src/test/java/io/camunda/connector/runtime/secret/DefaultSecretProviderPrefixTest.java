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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifies that when no prefix is explicitly configured, the environment secret provider falls
 * back to the {@code SECRET_} default rather than exposing all environment variables.
 */
@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class},
    properties = {
      "camunda.connector.secretprovider.discovery.enabled=false",
      "test.secret=unprefixed value",
      "SECRET_test.secret=default prefixed value",
      "secret-without-prefix=leaked value"
    })
public class DefaultSecretProviderPrefixTest {

  @Autowired SecretProviderAggregator secretProviderAggregator;

  @Test
  void shouldDefaultToSecretPrefix() {
    var actualSecretValue = secretProviderAggregator.getSecret("test.secret", null);
    assertThat(actualSecretValue).isEqualTo("default prefixed value");
  }

  @Test
  void shouldNotResolveUnprefixedSecretByDefault() {
    var actualSecretValue = secretProviderAggregator.getSecret("secret-without-prefix", null);
    assertThat(actualSecretValue).isNull();
  }
}
