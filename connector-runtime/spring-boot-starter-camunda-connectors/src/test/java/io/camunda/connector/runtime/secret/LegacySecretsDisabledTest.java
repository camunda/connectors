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
 * camunda.connector.secret-resolver.legacy.enabled=false turns off {{secrets.X}} / bare secrets.X
 * for outbound, inbound and configuration validation alike, since all three share the one
 * SecretProviderAggregator bean this property gates. camunda.secrets.<name> is unaffected - it is a
 * separate mechanism with no off switch.
 */
@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class, FooSpringSecretProvider.class},
    properties = {"camunda.connector.secret-resolver.legacy.enabled=false"})
class LegacySecretsDisabledTest {

  @Autowired SecretProviderAggregator secretProviderAggregator;

  @Test
  void everyLookupThrowsInsteadOfResolving() {
    // FooSpringSecretProvider would normally resolve "FOO" - it must not be consulted at all.
    assertThatThrownBy(() -> secretProviderAggregator.getSecret("FOO", null))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("camunda.connector.secret-resolver.legacy.enabled=false");
  }
}
