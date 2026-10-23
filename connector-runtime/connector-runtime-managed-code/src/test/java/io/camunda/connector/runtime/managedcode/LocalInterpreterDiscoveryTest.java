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
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalInterpreterDiscoveryTest {

  @TempDir Path temporaryRoot;

  @Test
  void skipsVersionManagerShimThatCannotRunInTheSanitizedEnvironment() throws Exception {
    assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"));
    final var shimDirectory = Files.createDirectory(temporaryRoot.resolve("shims"));
    final var binaryDirectory = Files.createDirectory(temporaryRoot.resolve("bin"));
    executable(
        shimDirectory.resolve("node"),
        """
        #!/bin/sh
        if command -v runtime-manager >/dev/null 2>&1; then
          exec runtime-manager "$@"
        fi
        exit 126
        """);
    executable(
        shimDirectory.resolve("runtime-manager"),
        """
        #!/bin/sh
        echo v99.0.0
        """);
    final var realNode =
        executable(
            binaryDirectory.resolve("node"),
            """
            #!/bin/sh
            echo v22.22.3
            """);
    final var path =
        shimDirectory + File.pathSeparator + binaryDirectory + File.pathSeparator + "/usr/bin";

    final var status = LocalInterpreterDiscovery.discover(ScriptLanguage.JAVASCRIPT, path);

    assertThat(status.available()).isTrue();
    assertThat(status.executable()).contains(realNode);
    assertThat(status.version()).isEqualTo("v22.22.3");
  }

  private static Path executable(Path path, String content) throws Exception {
    Files.writeString(path, content.stripLeading());
    assertThat(path.toFile().setExecutable(true)).isTrue();
    return path.toAbsolutePath().normalize();
  }
}
