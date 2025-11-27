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

import io.camunda.connector.api.secret.SecretContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

public class EnvironmentSecretProviderTest {

  @Test
  void shouldApplyPrefix() {
    MockEnvironment env = new MockEnvironment();
    env.setProperty("secrets.my-total-secret", "beebop");
    EnvironmentSecretProvider secretProvider =
        new EnvironmentSecretProvider(env, "secrets.", false, false);
    String myTotalSecret = secretProvider.getSecret("my-total-secret", null);
    assertThat(myTotalSecret).isEqualTo("beebop");
  }

  @Test
  void shouldNotApplyPrefix() {
    MockEnvironment env = new MockEnvironment();
    env.setProperty("my-total-secret", "beebop");
    EnvironmentSecretProvider secretProvider =
        new EnvironmentSecretProvider(env, null, false, false);
    String myTotalSecret = secretProvider.getSecret("my-total-secret", null);
    assertThat(myTotalSecret).isEqualTo("beebop");
  }

  @Test
  void shouldApplyTenantId() {
    MockEnvironment env = new MockEnvironment();
    env.setProperty("my-tenant_my-total-secret", "beebop");
    EnvironmentSecretProvider secretProvider =
        new EnvironmentSecretProvider(env, null, true, false);
    String myTotalSecret =
        secretProvider.getSecret("my-total-secret", new SecretContext("my-tenant", "my-process"));
    assertThat(myTotalSecret).isEqualTo("beebop");
  }

  @Test
  void shouldApplyPrefixAndTenantId() {
    MockEnvironment env = new MockEnvironment();
    env.setProperty("secrets.my-tenant_my-total-secret", "beebop");
    EnvironmentSecretProvider secretProvider =
        new EnvironmentSecretProvider(env, "secrets.", true, false);
    String myTotalSecret =
        secretProvider.getSecret("my-total-secret", new SecretContext("my-tenant", "my-process"));
    assertThat(myTotalSecret).isEqualTo("beebop");
  }

  @Test
  void shouldApplyProcessDefinitionId() {
    MockEnvironment env = new MockEnvironment();
    env.setProperty("my-process_my-total-secret", "beebop");
    EnvironmentSecretProvider secretProvider =
        new EnvironmentSecretProvider(env, null, false, true);
    String myTotalSecret =
        secretProvider.getSecret("my-total-secret", new SecretContext("my-tenant", "my-process"));
    assertThat(myTotalSecret).isEqualTo("beebop");
  }

  @Test
  void shouldApplyPrefixAndProcessDefinitionId() {
    MockEnvironment env = new MockEnvironment();
    env.setProperty("secrets.my-process_my-total-secret", "beebop");
    EnvironmentSecretProvider secretProvider =
        new EnvironmentSecretProvider(env, "secrets.", false, true);
    String myTotalSecret =
        secretProvider.getSecret("my-total-secret", new SecretContext("my-tenant", "my-process"));
    assertThat(myTotalSecret).isEqualTo("beebop");
  }

  @Test
  void shouldApplyTenantIdAndProcessDefinitionId() {
    MockEnvironment env = new MockEnvironment();
    env.setProperty("my-tenant_my-process_my-total-secret", "beebop");
    EnvironmentSecretProvider secretProvider = new EnvironmentSecretProvider(env, null, true, true);
    String myTotalSecret =
        secretProvider.getSecret("my-total-secret", new SecretContext("my-tenant", "my-process"));
    assertThat(myTotalSecret).isEqualTo("beebop");
  }

  @Test
  void shouldApplyPrefixAndTenantIdAndProcessDefinitionId() {
    MockEnvironment env = new MockEnvironment();
    env.setProperty("secrets.my-tenant_my-process_my-total-secret", "beebop");
    EnvironmentSecretProvider secretProvider =
        new EnvironmentSecretProvider(env, "secrets.", true, true);
    String myTotalSecret =
        secretProvider.getSecret("my-total-secret", new SecretContext("my-tenant", "my-process"));
    assertThat(myTotalSecret).isEqualTo("beebop");
  }
}
