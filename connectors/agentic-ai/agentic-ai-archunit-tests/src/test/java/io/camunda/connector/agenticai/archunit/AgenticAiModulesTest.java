/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AgenticAiModulesTest {

  private static final String CLASS_FILE =
      "!/io/camunda/connector/agenticai/a2a/client/common/A2aConstants.class";

  @ParameterizedTest
  @ValueSource(
      strings = {
        "file:/work/connectors/agentic-ai/connector-a2a/target/classes/io/camunda/connector/agenticai/a2a/client/common/A2aConstants.class",
        "jar:file:/work/connector-a2a/target/connector-a2a-8.11.0-SNAPSHOT.jar" + CLASS_FILE,
        "jar:file:/work/connector-a2a/target/connector-a2a-8.11.0.jar" + CLASS_FILE,
        "jar:file:/work/connector-a2a/target/connector-a2a-8.11.12.jar" + CLASS_FILE,
        "jar:file:/work/connector-a2a/target/connector-a2a-8.11.0-alpha1.jar" + CLASS_FILE,
        "jar:file:/work/connector-a2a/target/connector-a2a-8.11.0-alpha12.jar" + CLASS_FILE,
        "jar:file:/work/connector-a2a/target/connector-a2a-8.11.0-rc1.jar" + CLASS_FILE,
        "jar:file:/work/connector-a2a/target/connector-a2a-8.11.0-rc12.jar" + CLASS_FILE
      })
  void locatesClassesInTheModuleBuildOutput(final String location) {
    assertThat(AgenticAiModules.A2A_CLIENT.isLocatedIn(location)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "jar:file:/work/connector-a2a/target/connector-a2a-8.11.jar" + CLASS_FILE,
        "jar:file:/work/connector-a2a/target/connector-a2a-8.11.0-alpha.jar" + CLASS_FILE,
        "jar:file:/work/connector-a2a/target/connector-a2a-8.11.0-rc.jar" + CLASS_FILE,
        "jar:file:/work/connector-a2a/target/connector-a2a-8.11.0-beta1.jar" + CLASS_FILE,
        "jar:file:/work/connector-a2a/target/connector-a2a-other-8.11.0.jar" + CLASS_FILE
      })
  void doesNotLocateClassesOutsideTheModuleBuildOutput(final String location) {
    assertThat(AgenticAiModules.A2A_CLIENT.isLocatedIn(location)).isFalse();
  }
}
