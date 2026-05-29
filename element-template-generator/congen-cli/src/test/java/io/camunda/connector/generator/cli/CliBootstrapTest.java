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
package io.camunda.connector.generator.cli;

import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.connector.generator.cli.command.ConGen;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/**
 * Smoke tests guarding against startup-time crashes in the CLI. picocli reflects over every
 * subcommand class when the root {@link CommandLine} is constructed, so any failing {@code static}
 * initializer in a subcommand wedges every entry point — including {@code -h}, {@code list}, and
 * {@code scan} that don't touch the failing class otherwise.
 */
class CliBootstrapTest {

  @Test
  void commandLineCanBeConstructed() {
    assertThatCode(() -> new CommandLine(new ConGen())).doesNotThrowAnyException();
  }

  @Test
  void helpFlagReturnsZeroExitCode() {
    int exitCode =
        new CommandLine(new ConGen()).setUnmatchedOptionsArePositionalParams(true).execute("-h");
    assertThatCode(() -> {}).doesNotThrowAnyException();
    if (exitCode != 0) {
      throw new AssertionError("expected -h to exit 0, got " + exitCode);
    }
  }
}
