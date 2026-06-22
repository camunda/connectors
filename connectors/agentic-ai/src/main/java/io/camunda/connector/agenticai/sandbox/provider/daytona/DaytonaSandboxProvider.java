/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.provider.daytona;

import io.camunda.connector.agenticai.sandbox.spi.SandboxCapability;
import io.camunda.connector.agenticai.sandbox.spi.SandboxException;
import io.camunda.connector.agenticai.sandbox.spi.SandboxHandle;
import io.camunda.connector.agenticai.sandbox.spi.SandboxProvider;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSession;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSpec;
import io.daytona.sdk.Daytona;
import io.daytona.sdk.DaytonaConfig;
import io.daytona.sdk.Sandbox;
import io.daytona.sdk.exception.DaytonaException;
import io.daytona.sdk.model.CreateSandboxFromSnapshotParams;
import java.util.EnumSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link SandboxProvider} implementation backed by the Daytona cloud sandbox platform.
 *
 * <p>Each session creates a dedicated {@link Daytona} HTTP client instance that is closed when the
 * session terminates. The sandbox itself is NOT deleted on session close — Daytona's auto-stop /
 * auto-archive intervals manage idle lifecycle so the sandbox persists across agent invocations.
 */
public class DaytonaSandboxProvider implements SandboxProvider {

  public static final String PROVIDER_ID = "daytona";

  private static final Logger log = LoggerFactory.getLogger(DaytonaSandboxProvider.class);

  private static final Set<SandboxCapability> CAPABILITIES =
      EnumSet.of(
          SandboxCapability.FS_PERSIST_ACROSS_CONNECTIONS,
          SandboxCapability.SNAPSHOT,
          SandboxCapability.FORK,
          SandboxCapability.FILE_SEARCH,
          SandboxCapability.SELF_HOSTABLE);

  private final String apiKey;
  @Nullable private final String apiUrl;

  public DaytonaSandboxProvider(String apiKey, @Nullable String apiUrl) {
    this.apiKey = apiKey;
    this.apiUrl = apiUrl;
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
    Daytona daytona = buildClient();
    try {
      Sandbox sandbox = createSandbox(daytona, spec);
      applyIntervals(sandbox, spec);
      log.debug("Created Daytona sandbox id={}", sandbox.getId());
      return new DaytonaSandboxSession(daytona, sandbox);
    } catch (DaytonaException e) {
      closeQuietly(daytona);
      throw new SandboxException("Failed to create Daytona sandbox: " + e.getMessage(), e);
    }
  }

  @Override
  public SandboxSession connect(SandboxHandle handle) {
    if (!PROVIDER_ID.equals(handle.providerId())) {
      throw new SandboxException(
          "Provider mismatch: expected " + PROVIDER_ID + " but got " + handle.providerId());
    }
    Daytona daytona = buildClient();
    try {
      Sandbox sandbox = daytona.get(handle.sessionId());
      // If the sandbox is stopped or archived, start it so it becomes usable again.
      String state = sandbox.getState();
      if (state != null
          && (state.equalsIgnoreCase("stopped") || state.equalsIgnoreCase("archived"))) {
        log.debug("Daytona sandbox id={} is in state '{}', starting it", sandbox.getId(), state);
        sandbox.start();
      }
      log.debug("Connected to Daytona sandbox id={} state={}", sandbox.getId(), state);
      return new DaytonaSandboxSession(daytona, sandbox);
    } catch (DaytonaException e) {
      closeQuietly(daytona);
      throw new SandboxException(
          "Failed to connect to Daytona sandbox '" + handle.sessionId() + "': " + e.getMessage(),
          e);
    }
  }

  private Daytona buildClient() {
    DaytonaConfig.Builder builder = new DaytonaConfig.Builder().apiKey(apiKey);
    // Only set apiUrl if non-null and non-blank (self-hosted deployments)
    if (apiUrl != null && !apiUrl.isBlank()) {
      builder = builder.apiUrl(apiUrl);
    }
    return new Daytona(builder.build());
  }

  private static Sandbox createSandbox(Daytona daytona, SandboxSpec spec) {
    String snapshot = spec.snapshot();
    if (snapshot != null && !snapshot.isBlank()) {
      CreateSandboxFromSnapshotParams params = new CreateSandboxFromSnapshotParams();
      params.setSnapshot(snapshot);
      return daytona.create(params);
    }
    return daytona.create();
  }

  private static void applyIntervals(Sandbox sandbox, SandboxSpec spec) {
    if (spec.autoStopMinutes() != null) {
      sandbox.setAutostopInterval(spec.autoStopMinutes());
    }
    if (spec.autoArchiveMinutes() != null) {
      sandbox.setAutoArchiveInterval(spec.autoArchiveMinutes());
    }
  }

  private static void closeQuietly(Daytona daytona) {
    try {
      daytona.close();
    } catch (Exception ex) {
      log.debug("Failed to close Daytona client during error cleanup", ex);
    }
  }
}
