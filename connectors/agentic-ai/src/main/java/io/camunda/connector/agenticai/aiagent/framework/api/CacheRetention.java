/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.api;

/**
 * Prompt-cache retention preference passed via {@link ChatOptions}.
 *
 * <p>{@code SHORT} maps to each provider's default ephemeral retention, {@code LONG} to extended
 * retention; {@code NONE} strips cache markers entirely. Concrete breakpoint placement is
 * implementation-specific.
 *
 * <p>Part of the ADR-004 Phase 1 SPI scaffolding. Not yet wired into the runtime.
 */
public enum CacheRetention {
  NONE,
  SHORT,
  LONG
}
