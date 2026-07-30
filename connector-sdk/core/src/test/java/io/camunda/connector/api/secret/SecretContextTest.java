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
package io.camunda.connector.api.secret;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SecretContextTest {

  @Test
  void keepsTheProvidedPhysicalTenantId() {
    var context = new SecretContext("tenant", "process", "engine-1");

    assertThat(context.physicalTenantId()).isEqualTo("engine-1");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " "})
  void normalizesABlankPhysicalTenantIdToNull(String blank) {
    // clients surface an unset physical tenant as an empty string, providers should only ever
    // have to null-check
    var context = new SecretContext("tenant", "process", blank);

    assertThat(context.physicalTenantId()).isNull();
  }

  @Test
  void legacyConstructorLeavesThePhysicalTenantIdUnset() {
    var context = new SecretContext("tenant", "process");

    assertThat(context.tenantId()).isEqualTo("tenant");
    assertThat(context.processDefinitionId()).isEqualTo("process");
    assertThat(context.physicalTenantId()).isNull();
  }
}
