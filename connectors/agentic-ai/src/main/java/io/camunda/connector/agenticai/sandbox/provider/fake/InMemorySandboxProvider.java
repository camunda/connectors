/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.provider.fake;

import io.camunda.connector.agenticai.sandbox.spi.ExecRequest;
import io.camunda.connector.agenticai.sandbox.spi.ExecResult;
import io.camunda.connector.agenticai.sandbox.spi.SandboxCapability;
import io.camunda.connector.agenticai.sandbox.spi.SandboxException;
import io.camunda.connector.agenticai.sandbox.spi.SandboxHandle;
import io.camunda.connector.agenticai.sandbox.spi.SandboxProvider;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSession;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSpec;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class InMemorySandboxProvider implements SandboxProvider {

  public static final String PROVIDER_ID = "fake";

  private static final Set<SandboxCapability> CAPABILITIES =
      EnumSet.of(SandboxCapability.FS_PERSIST_ACROSS_CONNECTIONS);

  /** Per-session in-memory FS state: sessionId → file map. */
  private final Map<String, Map<String, byte[]>> sessions = new ConcurrentHashMap<>();

  private final Function<ExecRequest, ExecResult> defaultExecHandler;

  public InMemorySandboxProvider() {
    this(InMemorySandboxSession.ECHO_EXEC);
  }

  public InMemorySandboxProvider(Function<ExecRequest, ExecResult> defaultExecHandler) {
    this.defaultExecHandler = defaultExecHandler;
  }

  @Override
  public String id() {
    return PROVIDER_ID;
  }

  @Override
  public Set<SandboxCapability> capabilities() {
    return CAPABILITIES;
  }

  @Override
  public SandboxSession create(SandboxSpec spec) {
    String sessionId = UUID.randomUUID().toString();
    Map<String, byte[]> files = new ConcurrentHashMap<>();
    sessions.put(sessionId, files);
    SandboxHandle handle = new SandboxHandle(PROVIDER_ID, sessionId, null);
    return new InMemorySandboxSession(handle, files, defaultExecHandler);
  }

  @Override
  public SandboxSession connect(SandboxHandle handle) {
    if (!PROVIDER_ID.equals(handle.providerId())) {
      throw new SandboxException(
          "Provider mismatch: expected " + PROVIDER_ID + " but got " + handle.providerId());
    }
    Map<String, byte[]> files = sessions.get(handle.sessionId());
    if (files == null) {
      throw new SandboxException("Session not found: " + handle.sessionId());
    }
    return new InMemorySandboxSession(handle, files, defaultExecHandler);
  }
}
