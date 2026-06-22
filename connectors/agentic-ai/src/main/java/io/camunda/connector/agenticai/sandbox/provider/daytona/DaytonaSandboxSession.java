/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.provider.daytona;

import io.camunda.connector.agenticai.sandbox.spi.ExecRequest;
import io.camunda.connector.agenticai.sandbox.spi.ExecResult;
import io.camunda.connector.agenticai.sandbox.spi.SandboxException;
import io.camunda.connector.agenticai.sandbox.spi.SandboxFileSystem;
import io.camunda.connector.agenticai.sandbox.spi.SandboxHandle;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSession;
import io.daytona.sdk.Daytona;
import io.daytona.sdk.Sandbox;
import io.daytona.sdk.exception.DaytonaException;
import io.daytona.sdk.model.ExecuteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link SandboxSession} backed by a single Daytona {@link Sandbox}.
 *
 * <p><strong>Lifecycle note:</strong> {@link #terminate()} intentionally does NOT call {@code
 * sandbox.delete()} or {@code sandbox.stop()}. The sandbox must survive across agent invocations so
 * files and process state persist between turns. Daytona's auto-stop / auto-archive intervals
 * (configured via {@link Sandbox#setAutostopInterval} / {@link Sandbox#setAutoArchiveInterval})
 * handle idle cleanup. On termination we only close the HTTP client ({@link Daytona#close()}) to
 * release socket resources.
 *
 * <p><strong>exec stderr note:</strong> Daytona's toolbox API returns combined stdout + stderr in
 * {@code ExecuteResponse.getResult()}; there is no separate stderr field. The {@code stderr} field
 * in the returned {@link ExecResult} is always the empty string.
 */
class DaytonaSandboxSession implements SandboxSession {

  private static final Logger log = LoggerFactory.getLogger(DaytonaSandboxSession.class);

  private final Daytona daytona;
  private final Sandbox sandbox;
  private final DaytonaSandboxFileSystem fileSystem;

  DaytonaSandboxSession(Daytona daytona, Sandbox sandbox) {
    this.daytona = daytona;
    this.sandbox = sandbox;
    this.fileSystem = new DaytonaSandboxFileSystem(sandbox.fs);
  }

  @Override
  public SandboxHandle handle() {
    return new SandboxHandle(DaytonaSandboxProvider.PROVIDER_ID, sandbox.getId(), null);
  }

  @Override
  public ExecResult exec(ExecRequest req) {
    try {
      ExecuteResponse resp =
          sandbox.process.executeCommand(req.command(), req.cwd(), req.env(), req.timeoutSeconds());
      return mapExecResponse(resp);
    } catch (DaytonaException e) {
      throw new SandboxException(
          "Command execution failed in Daytona sandbox: " + e.getMessage(), e);
    }
  }

  /**
   * Maps a Daytona {@link ExecuteResponse} to an {@link ExecResult}.
   *
   * <ul>
   *   <li>{@code exitCode}: uses {@code getExitCode()} if non-null, otherwise falls back to 0
   *   <li>{@code stdout}: uses {@code getResult()} — this is the combined stdout+stderr output from
   *       the Daytona toolbox; there is no separate stderr stream
   *   <li>{@code stderr}: always empty string (Daytona does not separate stderr)
   *   <li>{@code truncated}: always false (truncation is handled upstream by {@code
   *       BashToolHandler} via {@code OutputBounds})
   * </ul>
   */
  static ExecResult mapExecResponse(ExecuteResponse resp) {
    int exitCode = resp.getExitCode() != null ? resp.getExitCode() : 0;
    String stdout = resp.getResult() != null ? resp.getResult() : "";
    return new ExecResult(exitCode, stdout, "", false);
  }

  @Override
  public SandboxFileSystem fs() {
    return fileSystem;
  }

  /**
   * Releases HTTP resources by closing the {@link Daytona} client. Does NOT stop or delete the
   * sandbox — persistence across invocations is intentional. Daytona auto-stop / auto-archive
   * manage idle lifecycle.
   */
  @Override
  public void terminate() {
    try {
      daytona.close();
    } catch (Exception e) {
      log.debug("Failed to close Daytona client on session termination", e);
    }
  }
}
