/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript.local;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.managedscript.api.ErrorCode;
import io.camunda.connector.managedscript.api.ExecutionRequest;
import io.camunda.connector.managedscript.api.ExecutionResponse;
import io.camunda.connector.managedscript.api.ExecutorWrapper;
import io.camunda.connector.managedscript.api.ManagedCodeProvider;
import io.camunda.connector.managedscript.api.ProvisioningException;
import io.camunda.connector.managedscript.api.ScriptLanguage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs managed scripts on the host of the runtime, with the wrapper of ADR-0008 started as a
 * process for every call, and the interpreter installed on the host.
 *
 * <p><b>For trusted development only: it is not a sandbox.</b> A script runs with the permissions
 * of the user of the runtime. The wrapper only kills the process group of its script and removes
 * the temporary directory of its call, because the host is shared with other processes. Windows is
 * not supported.
 *
 * <p>The wrapper files are written to {@code <directory>/<executor name>}, where the name comes
 * from the digest of the files like the name of an executor of another provider, so that runtimes
 * of different releases share a directory.
 */
public class LocalManagedCodeProvider implements ManagedCodeProvider {

  public static final String NAME = "local";

  private static final Logger LOG = LoggerFactory.getLogger(LocalManagedCodeProvider.class);

  /**
   * What a script wrote with {@code console} or {@code print}, which the wrapper keeps at 4 KB per
   * call. A logger of its own, so that it can be routed or silenced apart from the provider's.
   */
  private static final Logger SCRIPT_LOG =
      LoggerFactory.getLogger("io.camunda.connector.managedscript.local.script");

  private static final int MAX_SCRIPT_LOG_BYTES = 8 * 1024;

  /** Identifies the provider in the digest of the wrapper: the interpreter is the host's. */
  private static final String WRAPPER_RUNTIME = "local";

  /** What the wrapper needs beyond the timeout of the script to start, answer and clean up. */
  private static final Duration CALL_GRACE = Duration.ofSeconds(10);

  /** The response of the wrapper holds at most 1 MB of output: this leaves room for the rest. */
  private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;

  private final Path directory;
  private final Path scratch;
  private final ObjectMapper mapper;
  private final InterpreterLocator locator;
  private final Duration callGrace;

  private volatile Map<ScriptLanguage, Executor> executors;

  public LocalManagedCodeProvider(Path directory, ObjectMapper mapper) {
    this(directory, mapper, InterpreterLocator.onPath());
  }

  LocalManagedCodeProvider(Path directory, ObjectMapper mapper, InterpreterLocator locator) {
    this(directory, mapper, locator, CALL_GRACE);
  }

  LocalManagedCodeProvider(
      Path directory, ObjectMapper mapper, InterpreterLocator locator, Duration callGrace) {
    this.callGrace = callGrace;
    this.directory = directory.toAbsolutePath();
    this.scratch = this.directory.resolve("tmp");
    this.mapper = mapper;
    this.locator = locator;
    LOG.warn(
        "The local managed-code provider runs scripts as processes of this runtime, on this host,"
            + " with the permissions of its user. It is for trusted development only and is not a"
            + " sandbox. Wrapper files: {}",
        this.directory);
  }

  @Override
  public String name() {
    return NAME;
  }

  /**
   * Writes the wrapper files of every language whose interpreter is installed, and checks them
   * against the digest of the wrapper. A language without a suitable interpreter fails its own
   * scripts, so that a host with only Node.js still runs JavaScript.
   *
   * @throws ProvisioningException when no language can run, or the host is Windows
   */
  @Override
  public synchronized void ensureExecutors() {
    requireSupportedHost();
    final var ready = new EnumMap<ScriptLanguage, Executor>(ScriptLanguage.class);
    final var unavailable = new ArrayList<String>();
    for (var runtime : LocalRuntime.values()) {
      try {
        ready.put(runtime.language(), prepare(runtime));
      } catch (ProvisioningException e) {
        if (e.code() == ErrorCode.PLATFORM_INTERRUPTED) {
          throw e;
        }
        LOG.info(
            "Local managed scripts: no {} scripts: {}",
            runtime.language().language(),
            e.getMessage());
        unavailable.add(e.getMessage());
      }
    }
    if (ready.isEmpty()) {
      throw new ProvisioningException(
          ErrorCode.PLATFORM_REJECTED,
          "No script can run on this host. " + String.join(". ", unavailable));
    }
    executors = Map.copyOf(ready);
  }

  @Override
  public ExecutionResponse execute(ScriptLanguage language, ExecutionRequest request) {
    final var current = executors;
    if (current == null) {
      return ExecutionResponse.failed(
          ErrorCode.DEPLOYMENT_MISSING, "The local executors have not been prepared");
    }
    final Executor executor;
    try {
      executor = current.containsKey(language) ? current.get(language) : prepareAgain(language);
    } catch (ProvisioningException e) {
      return ExecutionResponse.failed(e.code(), e.getMessage());
    }
    final var missing = executor.missingFile();
    if (missing != null) {
      return ExecutionResponse.failed(
          ErrorCode.DEPLOYMENT_MISSING, "The wrapper file " + missing + " is missing");
    }
    final byte[] input;
    try {
      input = mapper.writeValueAsBytes(request.payload());
    } catch (JsonProcessingException e) {
      return ExecutionResponse.failed(
          ErrorCode.INVALID_INPUT, "The input of the script cannot be serialized");
    }
    try {
      return call(
          executor,
          request.executionId(),
          input,
          Duration.ofMillis(request.timeoutMs()).plus(callGrace));
    } catch (IOException e) {
      return ExecutionResponse.failed(
          ErrorCode.DEPLOYMENT_MISSING,
          "Cannot run the local wrapper with " + executor.interpreter() + ": " + e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return ExecutionResponse.failed(
          ErrorCode.PLATFORM_INTERRUPTED, "The local call was interrupted");
    }
  }

  /** The interpreter this provider runs a language with, once its executors are ready. */
  Path interpreter(ScriptLanguage language) {
    final var current = executors;
    return current == null || !current.containsKey(language)
        ? null
        : current.get(language).interpreter();
  }

  /**
   * Looks again for the interpreter of a language that had none when the executors were prepared,
   * so that installing it does not take a restart of the runtime. Only a failed job gets here.
   */
  private synchronized Executor prepareAgain(ScriptLanguage language) {
    final var current = executors;
    if (current.containsKey(language)) {
      return current.get(language);
    }
    final var executor = prepare(LocalRuntime.of(language));
    final var ready = new EnumMap<ScriptLanguage, Executor>(current);
    ready.put(language, executor);
    executors = Map.copyOf(ready);
    return executor;
  }

  private Executor prepare(LocalRuntime runtime) {
    final var interpreter = locator.locate(runtime, scratch);
    final var wrapper = wrapper(runtime);
    return new Executor(runtime, interpreter, install(runtime, wrapper), wrapper.files().keySet());
  }

  private void requireSupportedHost() {
    if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
      throw new ProvisioningException(
          ErrorCode.PLATFORM_REJECTED, "The local managed-code provider does not support Windows");
    }
    try {
      createPrivateDirectory(directory);
      createPrivateDirectory(scratch);
    } catch (IOException e) {
      throw new ProvisioningException(
          ErrorCode.PLATFORM_REJECTED, "Cannot create " + directory + ": " + e.getMessage(), e);
    }
  }

  private ExecutionResponse call(
      Executor executor, String executionId, byte[] input, Duration deadline)
      throws IOException, InterruptedException {
    final var command = new ArrayList<String>();
    command.add(executor.interpreter().toString());
    command.addAll(executor.runtime().runArguments());
    command.add(executor.entryPoint().toString());
    final var builder = new ProcessBuilder(command).directory(scratch.toFile());
    // Nothing of the environment of the runtime: it holds the credentials of the connectors.
    final var environment = builder.environment();
    environment.clear();
    for (var name : List.of("PATH", "HOME", "LANG")) {
      final var value = System.getenv(name);
      if (value != null) {
        environment.put(name, value);
      }
    }
    // A directory of this call, removed whatever happens to the wrapper: when it is killed at the
    // deadline, or crashes, its own cleanup does not run, and the script and its output would stay.
    final var callDirectory = Files.createTempDirectory(scratch, "call-");
    environment.put("EXECUTOR_TMP", callDirectory.toString());
    final ProcessRunner.Result result;
    try {
      result =
          ProcessRunner.run(
              builder, input, deadline, MAX_RESPONSE_BYTES, MAX_SCRIPT_LOG_BYTES, callDirectory);
    } finally {
      removeCallDirectory(callDirectory);
    }
    if (!result.stderr().isBlank()) {
      SCRIPT_LOG.info("Logs of the script of execution {}:\n{}", executionId, result.stderr());
    }
    if (result.timedOut()) {
      return ExecutionResponse.failed(
          ErrorCode.PLATFORM_EXECUTION_FAILED,
          "The local wrapper did not answer within " + deadline.toSeconds() + " seconds");
    }
    if (result.outputTooLarge()) {
      return ExecutionResponse.failed(
          ErrorCode.PLATFORM_EXECUTION_FAILED, "The response of the local wrapper is too large");
    }
    try {
      final var response = mapper.readValue(result.stdout(), ExecutionResponse.class);
      if (response != null) {
        return response;
      }
    } catch (IOException e) {
      // Not a response: reported below, with the exit code.
    }
    return ExecutionResponse.failed(
        ErrorCode.PLATFORM_EXECUTION_FAILED,
        "The local wrapper exited with code " + result.exitCode() + " without a valid response");
  }

  /** Best effort: whatever stays is in a directory that only the user of the runtime can read. */
  private static void removeCallDirectory(Path callDirectory) {
    try (var files = Files.walk(callDirectory)) {
      for (var file : files.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(file);
      }
    } catch (IOException | UncheckedIOException e) {
      LOG.warn("Cannot remove the directory {} of a local call: {}", callDirectory, e.getMessage());
    }
  }

  /**
   * Writes the files of the wrapper that are absent, as a concurrent runtime may, and checks that
   * the directory holds exactly the wrapper of this runtime.
   */
  private Path install(LocalRuntime runtime, ExecutorWrapper wrapper) {
    final var target = directory.resolve(wrapper.executorName("", WRAPPER_RUNTIME));
    try {
      createPrivateDirectory(target);
      for (var file : wrapper.files().entrySet()) {
        writeIfAbsent(target, file.getKey(), file.getValue());
      }
      final var installed = readInstalled(target);
      if (installed == null
          || !new ExecutorWrapper(runtime.language(), installed)
              .digest(WRAPPER_RUNTIME)
              .equals(wrapper.digest(WRAPPER_RUNTIME))) {
        throw new ProvisioningException(
            ErrorCode.PLATFORM_REJECTED,
            "The files in %s do not match the wrapper of this runtime: remove the directory"
                .formatted(target));
      }
      return target;
    } catch (IOException e) {
      throw new ProvisioningException(
          ErrorCode.PLATFORM_REJECTED,
          "Cannot write the wrapper files to %s: %s".formatted(target, e.getMessage()),
          e);
    }
  }

  private static ExecutorWrapper wrapper(LocalRuntime runtime) {
    try (InputStream in =
        LocalManagedCodeProvider.class.getResourceAsStream(runtime.resourcePath())) {
      if (in == null) {
        throw new IllegalStateException("Missing local entry point " + runtime.resourcePath());
      }
      return ExecutorWrapper.of(runtime.language())
          .with(Map.of(runtime.entryPoint(), in.readAllBytes()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Written next to the target and moved into it, so that nobody reads half a file. */
  private void writeIfAbsent(Path target, String name, byte[] content) throws IOException {
    final var file = target.resolve(name);
    if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    final var part = Files.createTempFile(scratch, name, ".part");
    try {
      Files.write(part, content);
      Files.move(part, file, StandardCopyOption.ATOMIC_MOVE);
    } finally {
      Files.deleteIfExists(part);
    }
  }

  /**
   * The files of the directory, or {@code null} when it holds anything but regular files. A file
   * that another user owns or can write is refused: it is run by every call.
   */
  private static Map<String, byte[]> readInstalled(Path target) throws IOException {
    final var files = new TreeMap<String, byte[]>();
    try (var entries = Files.list(target)) {
      for (var entry : entries.toList()) {
        if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
          return null;
        }
        requireOnlyWritableByUser(entry);
        files.put(entry.getFileName().toString(), Files.readAllBytes(entry));
      }
    }
    return files;
  }

  /**
   * Creates the directory with access for its owner only, or checks that an existing one is not a
   * link, belongs to the user of the runtime and cannot be written by anyone else: the files in it
   * are run, so whoever can replace them runs code as this user.
   */
  private static void createPrivateDirectory(Path path) throws IOException {
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      Files.createDirectories(
          path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    }
    if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
      throw new ProvisioningException(
          ErrorCode.PLATFORM_REJECTED, path + " must be a directory, not a link or a file");
    }
    requireOnlyWritableByUser(path);
  }

  /** The user of the runtime owns the file or directory, and nobody else can write to it. */
  private static void requireOnlyWritableByUser(Path path) throws IOException {
    final var owner = Files.getOwner(path, LinkOption.NOFOLLOW_LINKS).getName();
    if (!owner.equals(System.getProperty("user.name"))) {
      throw new ProvisioningException(
          ErrorCode.PLATFORM_REJECTED,
          "%s belongs to %s, not to the user of the runtime".formatted(path, owner));
    }
    final var permissions = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
    if (permissions.contains(PosixFilePermission.GROUP_WRITE)
        || permissions.contains(PosixFilePermission.OTHERS_WRITE)) {
      throw new ProvisioningException(
          ErrorCode.PLATFORM_REJECTED,
          "%s can be written by other users: remove that access, for example with chmod go-w"
              .formatted(path));
    }
  }

  /**
   * @param files names of the files of the wrapper, in {@code directory}
   */
  private record Executor(
      LocalRuntime runtime, Path interpreter, Path directory, Set<String> files) {

    Path entryPoint() {
      return directory.resolve(runtime.entryPoint());
    }

    /** The first file of the wrapper that is gone, or {@code null}. */
    Path missingFile() {
      return files.stream()
          .map(directory::resolve)
          .filter(file -> !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
          .findFirst()
          .orElse(null);
    }
  }
}
