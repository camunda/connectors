/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.provider.daytona;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.sandbox.spi.ExecResult;
import io.daytona.sdk.model.ExecuteResponse;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for the Daytona SDK → SPI mapping logic in {@link DaytonaSandboxSession}. These
 * tests do not require a Daytona API key or network access.
 */
class DaytonaSandboxSessionMappingTest {

  @Test
  void mapExecResponse_normalResponse_mapsCorrectly() {
    ExecuteResponse resp = new ExecuteResponse();
    resp.setExitCode(0);
    resp.setResult("hello world\n");

    ExecResult result = DaytonaSandboxSession.mapExecResponse(resp);

    assertThat(result.exitCode()).isEqualTo(0);
    assertThat(result.stdout()).isEqualTo("hello world\n");
    assertThat(result.stderr()).isEmpty();
    assertThat(result.truncated()).isFalse();
  }

  @Test
  void mapExecResponse_nonZeroExitCode_isPreserved() {
    ExecuteResponse resp = new ExecuteResponse();
    resp.setExitCode(127);
    resp.setResult("command not found");

    ExecResult result = DaytonaSandboxSession.mapExecResponse(resp);

    assertThat(result.exitCode()).isEqualTo(127);
    assertThat(result.stdout()).isEqualTo("command not found");
    assertThat(result.stderr()).isEmpty();
    assertThat(result.truncated()).isFalse();
  }

  @Test
  void mapExecResponse_nullExitCode_defaultsToZero() {
    ExecuteResponse resp = new ExecuteResponse();
    resp.setExitCode(null);
    resp.setResult("some output");

    ExecResult result = DaytonaSandboxSession.mapExecResponse(resp);

    assertThat(result.exitCode()).isEqualTo(0);
    assertThat(result.stdout()).isEqualTo("some output");
  }

  @Test
  void mapExecResponse_nullResult_defaultsToEmptyString() {
    ExecuteResponse resp = new ExecuteResponse();
    resp.setExitCode(0);
    resp.setResult(null);

    ExecResult result = DaytonaSandboxSession.mapExecResponse(resp);

    assertThat(result.exitCode()).isEqualTo(0);
    assertThat(result.stdout()).isEmpty();
  }

  @Test
  void mapExecResponse_bothNullFields_defaultsApplied() {
    ExecuteResponse resp = new ExecuteResponse();
    // neither setExitCode nor setResult called — both null

    ExecResult result = DaytonaSandboxSession.mapExecResponse(resp);

    assertThat(result.exitCode()).isEqualTo(0);
    assertThat(result.stdout()).isEmpty();
    assertThat(result.stderr()).isEmpty();
    assertThat(result.truncated()).isFalse();
  }

  @Test
  void mapExecResponse_stderrAlwaysEmpty() {
    // Daytona returns combined stdout+stderr in result — stderr SPI field is always ""
    ExecuteResponse resp = new ExecuteResponse();
    resp.setExitCode(1);
    resp.setResult("error: something went wrong");

    ExecResult result = DaytonaSandboxSession.mapExecResponse(resp);

    assertThat(result.stderr()).isEmpty();
    assertThat(result.stdout()).isEqualTo("error: something went wrong");
  }
}
