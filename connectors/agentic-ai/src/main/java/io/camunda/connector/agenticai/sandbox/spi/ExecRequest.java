/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.spi;

import java.util.Map;
import org.jspecify.annotations.Nullable;

public record ExecRequest(
    String command,
    @Nullable String cwd,
    @Nullable Map<String, String> env,
    int timeoutSeconds,
    long maxOutputBytes) {

  public static final int DEFAULT_TIMEOUT_SECONDS = 60;
  public static final long DEFAULT_MAX_OUTPUT_BYTES = 1_048_576L; // 1 MB

  /** Convenience factory with sensible defaults. */
  public static ExecRequest of(String command) {
    return new ExecRequest(command, null, null, DEFAULT_TIMEOUT_SECONDS, DEFAULT_MAX_OUTPUT_BYTES);
  }
}
