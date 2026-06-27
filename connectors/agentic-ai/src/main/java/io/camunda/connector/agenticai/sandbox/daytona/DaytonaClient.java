/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.daytona;

import io.daytona.sdk.Daytona;
import io.daytona.sdk.DaytonaConfig;
import io.daytona.sdk.Sandbox;
import io.daytona.sdk.exception.DaytonaException;
import io.daytona.sdk.model.CreateSandboxFromSnapshotParams;
import io.daytona.sdk.model.ExecuteResponse;
import io.daytona.sdk.model.FileInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin client over the Daytona SDK that consolidates sandbox provisioning, connection, command
 * execution, and file-system operations into a single class.
 *
 * <p>All Daytona SDK exceptions are wrapped as {@link DaytonaClientException} so callers do not
 * need to import the SDK exception type.
 */
public class DaytonaClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(DaytonaClient.class);

  /**
   * Builds a Daytona SDK client from the given credentials.
   *
   * @param apiKey Daytona API key
   * @param apiUrl optional base URL for self-hosted deployments; {@code null} or blank uses the
   *     Daytona cloud default
   * @return configured {@link Daytona} client
   */
  public static Daytona buildClient(String apiKey, @Nullable String apiUrl) {
    DaytonaConfig.Builder builder = new DaytonaConfig.Builder().apiKey(apiKey);
    if (apiUrl != null && !apiUrl.isBlank()) {
      builder.apiUrl(apiUrl);
    }
    return new Daytona(builder.build());
  }

  /**
   * Creates a new sandbox, applying lifecycle intervals and process-level labels.
   *
   * @param daytona configured SDK client
   * @param spec lifecycle/snapshot parameters (minutes already resolved)
   * @param processInstanceKey Zeebe process instance key (informative label)
   * @param agentInstanceKey the agent instance key (informative label; may be null); the sandbox is
   *     addressed by the returned id, not by label
   * @return handle and working directory of the created sandbox
   */
  public DaytonaSandboxInfo create(
      Daytona daytona,
      SandboxCreateSpec spec,
      String processInstanceKey,
      @Nullable String agentInstanceKey) {
    try {
      CreateSandboxFromSnapshotParams params = new CreateSandboxFromSnapshotParams();

      // Set snapshot if configured
      if (spec.snapshot() != null && !spec.snapshot().isBlank()) {
        params.setSnapshot(spec.snapshot());
      }

      // Apply lifecycle intervals
      params.setAutoStopInterval(spec.autoStopMinutes());
      Integer archiveMinutes = spec.autoArchiveMinutes();
      if (archiveMinutes != null) {
        params.setAutoArchiveInterval(archiveMinutes);
      }
      Integer deleteMinutes = spec.autoDeleteMinutes();
      if (deleteMinutes != null) {
        params.setAutoDeleteInterval(deleteMinutes);
      }

      // Informative labels only (for humans and the future reaper). The sandbox is
      // always addressed by its returned id (the handle), never re-found by label.
      Map<String, String> labels = new HashMap<>();
      labels.put("processInstanceKey", processInstanceKey);
      if (agentInstanceKey != null && !agentInstanceKey.isBlank()) {
        labels.put("agentInstanceKey", agentInstanceKey);
      }
      params.setLabels(labels);

      Sandbox sandbox = daytona.create(params);
      LOGGER.debug(
          "Created Daytona sandbox id={} for processInstanceKey={} agentInstanceKey={}",
          sandbox.getId(),
          processInstanceKey,
          agentInstanceKey);
      return new DaytonaSandboxInfo(sandbox.getId(), workDir(sandbox));
    } catch (DaytonaException e) {
      throw new DaytonaClientException("Failed to create Daytona sandbox: " + e.getMessage(), e);
    }
  }

  /**
   * Connects to an existing sandbox by handle, restarting it if it is stopped or archived.
   *
   * @param daytona configured SDK client
   * @param handle sandbox id or name (as returned by {@link #create})
   * @return the connected {@link Sandbox}
   */
  public Sandbox connect(Daytona daytona, String handle) {
    try {
      Sandbox sandbox = daytona.get(handle);
      String state = sandbox.getState();
      if ("stopped".equals(state) || "archived".equals(state)) {
        LOGGER.debug("Restarting Daytona sandbox {} (state={})", handle, state);
        sandbox.start();
      }
      return sandbox;
    } catch (DaytonaException e) {
      throw new DaytonaClientException(
          "Failed to connect to Daytona sandbox '" + handle + "': " + e.getMessage(), e);
    }
  }

  /**
   * Returns the working directory for the sandbox. Falls back to the user home directory and then
   * to {@code /home/daytona} if neither is available.
   *
   * @param sandbox the connected sandbox
   * @return working directory path (no trailing slash)
   */
  public String workDir(Sandbox sandbox) {
    try {
      String dir = sandbox.getWorkDir();
      if (dir != null && !dir.isBlank()) {
        return stripTrailingSlash(dir);
      }
      dir = sandbox.getUserHomeDir();
      if (dir != null && !dir.isBlank()) {
        return stripTrailingSlash(dir);
      }
      return "/home/daytona";
    } catch (DaytonaException e) {
      LOGGER.warn(
          "Could not determine workDir for sandbox {}: {}", sandbox.getId(), e.getMessage());
      return "/home/daytona";
    }
  }

  /**
   * Executes a shell command in the sandbox.
   *
   * @param sandbox the connected sandbox
   * @param command shell command to execute
   * @param timeoutSeconds execution timeout in seconds
   * @return outcome containing exit code, stdout, and stderr
   */
  public ExecOutcome exec(Sandbox sandbox, String command, int timeoutSeconds) {
    try {
      ExecuteResponse response =
          sandbox.process.executeCommand(command, null, null, timeoutSeconds);
      int exitCode = response.getExitCode() != null ? response.getExitCode() : 0;
      String stdout = response.getResult() != null ? response.getResult() : "";
      return new ExecOutcome(exitCode, stdout, "");
    } catch (DaytonaException e) {
      throw new DaytonaClientException(
          "Failed to execute command in Daytona sandbox: " + e.getMessage(), e);
    }
  }

  /**
   * Downloads a file from the sandbox.
   *
   * @param sandbox the connected sandbox
   * @param path remote file path
   * @return file contents as bytes
   */
  public byte[] fsRead(Sandbox sandbox, String path) {
    try {
      return sandbox.fs.downloadFile(path);
    } catch (DaytonaException e) {
      throw new DaytonaClientException(
          "Failed to read file '" + path + "' from Daytona sandbox: " + e.getMessage(), e);
    }
  }

  /**
   * Uploads content to a file in the sandbox, creating parent directories as needed.
   *
   * @param sandbox the connected sandbox
   * @param path remote file path
   * @param content file bytes to write
   */
  public void fsWrite(Sandbox sandbox, String path, byte[] content) {
    DaytonaException folderCreationError = null;
    String parent = parentDir(path);
    if (parent != null && !parent.isBlank()) {
      try {
        sandbox.fs.createFolder(parent, "755");
      } catch (DaytonaException e) {
        // Swallow — the folder may already exist; we'll report it if upload also fails
        folderCreationError = e;
      }
    }
    try {
      sandbox.fs.uploadFile(content, path);
    } catch (DaytonaException uploadError) {
      String msg =
          "Failed to write file '" + path + "' to Daytona sandbox: " + uploadError.getMessage();
      if (folderCreationError != null) {
        msg +=
            " (also failed to create parent directory: " + folderCreationError.getMessage() + ")";
      }
      throw new DaytonaClientException(msg, uploadError);
    }
  }

  /**
   * Lists files under a path in the sandbox.
   *
   * @param sandbox the connected sandbox
   * @param path directory path
   * @return list of file metadata entries
   */
  public List<FileInfo> fsList(Sandbox sandbox, String path) {
    try {
      return sandbox.fs.listFiles(path);
    } catch (DaytonaException e) {
      throw new DaytonaClientException(
          "Failed to list files at '" + path + "' in Daytona sandbox: " + e.getMessage(), e);
    }
  }

  private static String stripTrailingSlash(String path) {
    return path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
  }

  @Nullable
  private static String parentDir(String path) {
    if (path == null) return null;
    int lastSlash = path.lastIndexOf('/');
    if (lastSlash <= 0) return null;
    return path.substring(0, lastSlash);
  }

  /** Lifecycle/snapshot parameters for sandbox creation (minutes already resolved). */
  public record SandboxCreateSpec(
      @Nullable String snapshot,
      @Nullable Integer autoStopMinutes,
      @Nullable Integer autoArchiveMinutes,
      @Nullable Integer autoDeleteMinutes) {}

  /** Result of a sandbox provisioning operation. */
  public record DaytonaSandboxInfo(String handle, String workDir) {}

  /** Result of a command execution in the sandbox. */
  public record ExecOutcome(int exitCode, String stdout, String stderr) {}

  /** Wraps Daytona SDK exceptions so callers do not need to import the SDK exception type. */
  public static class DaytonaClientException extends RuntimeException {
    public DaytonaClientException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
