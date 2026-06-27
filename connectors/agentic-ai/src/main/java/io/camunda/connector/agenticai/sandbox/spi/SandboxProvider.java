/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.spi;

import java.util.Set;

public interface SandboxProvider {

  /** Unique provider identifier, e.g. {@code "fake"}, {@code "daytona"}, {@code "e2b"}. */
  String id();

  Set<SandboxCapability> capabilities();

  SandboxSession create(SandboxSpec spec);

  SandboxSession connect(SandboxHandle handle);
}
