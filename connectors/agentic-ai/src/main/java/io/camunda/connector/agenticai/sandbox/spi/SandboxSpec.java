/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.spi;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Describes a fresh sandbox session request. All fields are optional. */
public record SandboxSpec(
    @Nullable String snapshot,
    @Nullable String template,
    @Nullable Map<String, String> env,
    @Nullable Integer autoStopMinutes,
    @Nullable Integer autoArchiveMinutes,
    @Nullable Integer autoDeleteMinutes,
    @Nullable String startupScript) {

  /**
   * Sensible defaults: no snapshot, no template, no env overrides, provider defaults for TTL, no
   * startup script.
   */
  public static SandboxSpec defaults() {
    return new SandboxSpec(null, null, null, null, null, null, null);
  }
}
