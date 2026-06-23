/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.spi;

import java.util.Optional;

public interface SandboxSession extends AutoCloseable {

  SandboxHandle handle();

  /**
   * The absolute working directory of the sandbox — the writable base where the agent's files
   * (materialized skills, exported artifacts, scratch files) live.
   *
   * <p>Internal tools build paths relative to this rather than assuming a fixed mount point: the
   * writable root varies by provider and image (e.g. Daytona's default user home {@code
   * /home/daytona}), and a hardcoded path such as {@code /workspace} may not exist or may be
   * unwritable, causing file writes to fail.
   *
   * @return the absolute working directory, without a trailing slash
   */
  String workDir();

  ExecResult exec(ExecRequest req);

  SandboxFileSystem fs();

  void terminate();

  @Override
  default void close() {
    terminate();
  }

  /**
   * Probes for an optional capability interface (e.g. {@link Pausable}, {@link Snapshotable}).
   * Returns {@link Optional#empty()} unless the implementation supports the interface.
   */
  default <T> Optional<T> as(Class<T> capabilityInterface) {
    return Optional.empty();
  }
}
