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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.Assert.assertArrayEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

public class EnvironmentSecretProviderTest {

  @Test
  void shouldApplyPrefix() {
    MockEnvironment env = new MockEnvironment();
    env.setProperty("secrets.my-total-secret", "beebop");
    EnvironmentSecretProvider secretProvider = new EnvironmentSecretProvider(env, "secrets.");
    String myTotalSecret = secretProvider.getSecret("my-total-secret");
    assertThat(myTotalSecret).isEqualTo("beebop");
  }

  @Test
  void shouldReturnAllSecretsValues() {
    MockEnvironment env = new MockEnvironment();
    env.setProperty("secrets.my-total-secret", "beebop");
    env.setProperty("secrets.my-total-secret2", "beebop2");
    EnvironmentSecretProvider secretProvider = new EnvironmentSecretProvider(env, "secrets.");
    List<String> myTotalSecret = secretProvider.getSecretValues();
    assertArrayEquals(new String[] {"beebop", "beebop2"}, myTotalSecret.toArray());
  }
}
