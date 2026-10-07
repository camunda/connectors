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
package io.camunda.connector.runtime.managedcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ArtifactSpecTest {

  @Test
  void digestIsStableAndIgnoresTheResourceName() {
    final var first = artifact("tenant-a", "nodejs22", "sum.js", "script", null);
    final var second = artifact("tenant-a", " NodeJS22 ", "renamed.js", "script", null);

    assertThat(second.digest()).isEqualTo(first.digest()).hasSize(64);
    assertThat(second.deploymentName()).isEqualTo(first.deploymentName());
  }

  @Test
  void digestCoversScriptRuntimeAndDependencies() {
    final var base = artifact("tenant-a", "nodejs22", "sum.js", "script", null);

    assertThat(artifact("tenant-a", "nodejs22", "sum.js", "script2", null).digest())
        .isNotEqualTo(base.digest());
    assertThat(artifact("tenant-a", "nodejs24", "sum.js", "script", null).digest())
        .isNotEqualTo(base.digest());
    assertThat(artifact("tenant-a", "nodejs22", "sum.js", "script", "").digest())
        .isNotEqualTo(base.digest());
    assertThat(artifact("tenant-a", "nodejs22", "sum.js", "script", "{}").digest())
        .isNotEqualTo(artifact("tenant-a", "nodejs22", "sum.js", "script", "").digest());
  }

  @Test
  void deploymentNameIsPerTenantForTheSameDigest() {
    final var tenantA = artifact("tenant-a", "nodejs22", "sum.js", "script", null);
    final var tenantB = artifact("tenant-b", "nodejs22", "sum.js", "script", null);

    assertThat(tenantB.digest()).isEqualTo(tenantA.digest());
    assertThat(tenantB.deploymentName())
        .isNotEqualTo(tenantA.deploymentName())
        .startsWith("camunda-ms-")
        .hasSize("camunda-ms-".length() + 40);
  }

  @Test
  void languageMustMatchRuntimeAndExtension() {
    assertThat(ScriptLanguage.from("Python", "python3", "calculate.py"))
        .isEqualTo(ScriptLanguage.PYTHON);
    assertThatThrownBy(() -> ScriptLanguage.from("javascript", "python3", "sum.js"))
        .hasMessageContaining("incompatible");
    assertThatThrownBy(() -> ScriptLanguage.from("ruby", "ruby3", "sum.rb"))
        .hasMessageContaining("Unsupported");
  }

  private static ArtifactSpec artifact(
      String tenantId, String runtime, String resourceName, String script, String dependencies) {
    return ArtifactSpec.create(
        "default",
        tenantId,
        "local",
        ScriptLanguage.JAVASCRIPT,
        runtime,
        resourceName,
        script.getBytes(StandardCharsets.UTF_8),
        Optional.ofNullable(dependencies).map(value -> value.getBytes(StandardCharsets.UTF_8)));
  }
}
