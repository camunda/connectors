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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.secret.providers.FooSpringSecretProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Switching legacy resolution off applies to outbound, inbound and configuration validation alike,
 * because all three share the one aggregator bean the setting gates. It does not affect {@code
 * camunda.secrets.<name>}, which is a separate mechanism with no off switch.
 */
@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class, FooSpringSecretProvider.class},
    properties = {"camunda.connector.secret-resolver.legacy.mode=OFF"})
class LegacySecretsDisabledTest {

  @Autowired SecretProviderAggregator secretProviderAggregator;

  @Test
  void everyLookupFailsInsteadOfResolving() {
    // FooSpringSecretProvider would normally resolve "FOO"; it must not be consulted at all.
    assertThatThrownBy(() -> secretProviderAggregator.getSecret("FOO", null))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("camunda.connector.secret-resolver.legacy.mode=OFF")
        .hasMessageContaining("camunda.secrets.<name>");
  }
}
