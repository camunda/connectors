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
package io.camunda.connector.e2e.agenticai.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
class RealLlmTestEnvironmentTest {

  @SystemStub private final EnvironmentVariables environment = new EnvironmentVariables();

  @Test
  void shouldRequireEveryEnvironmentVariableToBeNonBlank() {
    environment.set("PRESENT_SECRET", "secret");
    environment.set("BLANK_SECRET", " ");

    assertThat(RealLlmTestEnvironment.hasNonBlankValues(List.of())).isTrue();
    assertThat(RealLlmTestEnvironment.hasNonBlankValues(List.of("PRESENT_SECRET"))).isTrue();
    assertThat(RealLlmTestEnvironment.hasNonBlankValues(List.of("MISSING_SECRET"))).isFalse();
    assertThat(RealLlmTestEnvironment.hasNonBlankValues(List.of("BLANK_SECRET"))).isFalse();
  }

  @Test
  void shouldUseDefaultForMissingOrBlankValues() {
    environment.set("PRESENT_VALUE", "value");
    environment.set("BLANK_VALUE", "");

    assertThat(RealLlmTestEnvironment.getOrDefault("PRESENT_VALUE", "default")).isEqualTo("value");
    assertThat(RealLlmTestEnvironment.getOrDefault("MISSING_VALUE", "default"))
        .isEqualTo("default");
    assertThat(RealLlmTestEnvironment.getOrDefault("BLANK_VALUE", "default")).isEqualTo("default");
  }
}
