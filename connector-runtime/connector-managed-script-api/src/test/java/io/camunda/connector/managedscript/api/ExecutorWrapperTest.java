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

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExecutorWrapperTest {

  private final ExecutorWrapper javascript = ExecutorWrapper.of(ScriptLanguage.JAVASCRIPT);
  private final ExecutorWrapper python = ExecutorWrapper.of(ScriptLanguage.PYTHON);

  @Test
  void packagesTheCoreTheChildAndTheLocalEntryPoint() {
    assertThat(javascript.files())
        .containsOnlyKeys("child.mjs", "executor_core.mjs", "local_call.mjs");
    assertThat(new String(javascript.files().get("executor_core.mjs"), StandardCharsets.UTF_8))
        .contains("export function createExecutor");
  }

  @Test
  void packagesThePythonCoreChildAndLocalEntryPoint() {
    assertThat(python.files()).containsOnlyKeys("child.py", "executor_core.py", "local_call.py");
    assertThat(new String(python.files().get("executor_core.py"), StandardCharsets.UTF_8))
        .contains("def create_executor(local, tmp)");
  }

  @Test
  void hasTheSameDigestForTheSameFilesAndRuntime() {
    assertThat(javascript.digest("nodejs22.x"))
        .isEqualTo(ExecutorWrapper.of(ScriptLanguage.JAVASCRIPT).digest("nodejs22.x"))
        .hasSize(64);
  }

  @Test
  void changesTheDigestWithTheRuntimeOrAnyFile() {
    final var digest = javascript.digest("nodejs22.x");

    assertThat(javascript.digest("nodejs24.x")).isNotEqualTo(digest);
    assertThat(javascript.with(Map.of("index.mjs", new byte[] {1})).digest("nodejs22.x"))
        .isNotEqualTo(digest);
  }

  @Test
  void namesAnExecutorFromThePrefixTheLanguageAndTheDigest() {
    assertThat(javascript.executorName("camunda-ms-", "nodejs22.x"))
        .isEqualTo("camunda-ms-js-" + javascript.digest("nodejs22.x").substring(0, 12));
  }

  @Test
  void namesAPythonExecutorWithItsOwnDigest() {
    assertThat(python.executorName("camunda-ms-", "python3.13"))
        .isEqualTo("camunda-ms-py-" + python.digest("python3.13").substring(0, 12));
    assertThat(python.digest("python3.13")).isNotEqualTo(javascript.digest("python3.13"));
  }
}
