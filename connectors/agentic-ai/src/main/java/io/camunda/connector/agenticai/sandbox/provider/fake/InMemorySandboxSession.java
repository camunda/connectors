/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.provider.fake;

import io.camunda.connector.agenticai.sandbox.spi.ExecRequest;
import io.camunda.connector.agenticai.sandbox.spi.ExecResult;
import io.camunda.connector.agenticai.sandbox.spi.SandboxFileSystem;
import io.camunda.connector.agenticai.sandbox.spi.SandboxHandle;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSession;
import java.util.Map;
import java.util.function.Function;

class InMemorySandboxSession implements SandboxSession {

  private final SandboxHandle handle;
  private final InMemorySandboxFileSystem fileSystem;
  private final Function<ExecRequest, ExecResult> execHandler;

  /** Default exec handler: echoes the command on stdout with exit code 0. */
  static final Function<ExecRequest, ExecResult> ECHO_EXEC =
      req -> new ExecResult(0, req.command(), "", false);

  InMemorySandboxSession(
      SandboxHandle handle,
      Map<String, byte[]> files,
      Function<ExecRequest, ExecResult> execHandler) {
    this.handle = handle;
    this.fileSystem = new InMemorySandboxFileSystem(files);
    this.execHandler = execHandler;
  }

  @Override
  public SandboxHandle handle() {
    return handle;
  }

  @Override
  public ExecResult exec(ExecRequest req) {
    return execHandler.apply(req);
  }

  @Override
  public SandboxFileSystem fs() {
    return fileSystem;
  }

  @Override
  public void terminate() {
    // no-op for in-memory sessions
  }
}
