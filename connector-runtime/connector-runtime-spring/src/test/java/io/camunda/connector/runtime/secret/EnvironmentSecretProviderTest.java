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

import static org.assertj.core.api.Assertions.*;

import io.camunda.connector.api.secret.SecretContext.OutboundSecretContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

public class EnvironmentSecretProviderTest {

  @Test
  void shouldApplyPrefix() {
    MockEnvironment env = new MockEnvironment();
    env.setProperty("secrets.my-total-secret", "beebop");
    EnvironmentSecretProvider secretProvider =
        new EnvironmentSecretProvider(env, "secrets.", false);
    String myTotalSecret = secretProvider.getSecret("my-total-secret", null);
    assertThat(myTotalSecret).isEqualTo("beebop");
  }

  @Test
  void shouldNotApplyPrefix() {
    MockEnvironment env = new MockEnvironment();
    env.setProperty("my-total-secret", "beebop");
    EnvironmentSecretProvider secretProvider = new EnvironmentSecretProvider(env, null, false);
    String myTotalSecret = secretProvider.getSecret("my-total-secret", null);
    assertThat(myTotalSecret).isEqualTo("beebop");
  }

  @Test
  void shouldApplyContext() {
    MockEnvironment env = new MockEnvironment();
    env.setProperty("my-tenant_my-total-secret", "beebop");
    EnvironmentSecretProvider secretProvider = new EnvironmentSecretProvider(env, null, true);
    String myTotalSecret =
        secretProvider.getSecret(
            "my-total-secret", new OutboundSecretContext("my-tenant", "my-process"));
    assertThat(myTotalSecret).isEqualTo("beebop");
  }

  @Test
  void shouldApplyPrefixAndContext() {
    MockEnvironment env = new MockEnvironment();
    env.setProperty("secrets.my-tenant_my-total-secret", "beebop");
    EnvironmentSecretProvider secretProvider = new EnvironmentSecretProvider(env, "secrets.", true);
    String myTotalSecret =
        secretProvider.getSecret(
            "my-total-secret", new OutboundSecretContext("my-tenant", "my-process"));
    assertThat(myTotalSecret).isEqualTo("beebop");
  }
}
