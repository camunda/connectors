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
package io.camunda.connector.managedscript.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ManagedCodeSettingsTest {

  @Test
  void hasDefaults() {
    final var settings = new ManagedCodeSettings(" ", Duration.ZERO);

    assertThat(settings.deploymentNamePrefix()).isEqualTo("camunda-ms-");
    assertThat(settings.executionTimeout()).isEqualTo(Duration.ofSeconds(30));
  }

  @Test
  void keepsAValidPrefixWithoutItsSurroundingSpaces() {
    assertThat(new ManagedCodeSettings(" ce-0123-", null).deploymentNamePrefix())
        .isEqualTo("ce-0123-");
  }

  @Test
  void leavesRoomInTheNameForTheExecutorSuffix() {
    final var longest = "a".repeat(ManagedCodeSettings.MAX_DEPLOYMENT_NAME_PREFIX_LENGTH);

    assertThat(new ManagedCodeSettings(longest, null).deploymentNamePrefix()).isEqualTo(longest);
    assertThat(longest.length() + ManagedCodeSettings.EXECUTOR_NAME_SUFFIX_LENGTH).isEqualTo(63);
    assertThatThrownBy(() -> new ManagedCodeSettings(longest + "a", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at most 48");
  }

  @Test
  void refusesAPrefixThatIsNotLowercaseLettersDigitsAndHyphens() {
    assertThatThrownBy(() -> new ManagedCodeSettings("Camunda_", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("camunda.connector.managed-code.deployment-name-prefix 'Camunda_'");
    assertThatThrownBy(() -> new ManagedCodeSettings("1-", null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
