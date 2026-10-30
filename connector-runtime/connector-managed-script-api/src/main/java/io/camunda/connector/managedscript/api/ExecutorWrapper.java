/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.managedscript.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The trusted wrapper that every executor of a language runs, whatever the provider (ADR-0008): it
 * implements the execution contract, runs each script in a new child process and enforces the
 * limits and the cleanup. A provider packages these files with its own adapter and names its
 * executors from {@link #digest}, so that a runtime only calls executors whose wrapper it knows.
 *
 * <p>For JavaScript: {@code executor_core.mjs}, the shared core, which exports {@code
 * createExecutor}; {@code child.mjs}, the child runner; and {@code local_call.mjs}, an entry point
 * that runs one call read from stdin, in local mode.
 *
 * @param files file name to content, in name order
 */
public record ExecutorWrapper(ScriptLanguage language, Map<String, byte[]> files) {

  /** Characters of the digest in an executor name. */
  public static final int NAME_DIGEST_LENGTH = 12;

  private static final String ROOT = "/io/camunda/connector/managedscript/executor/";
  private static final List<String> JAVASCRIPT_FILES =
      List.of("child.mjs", "executor_core.mjs", "local_call.mjs");

  public ExecutorWrapper {
    final Map<String, byte[]> copy = new TreeMap<>();
    files.forEach((name, content) -> copy.put(name, content.clone()));
    files = Collections.unmodifiableMap(copy);
  }

  /** File name to a copy of its content, in name order. */
  @Override
  public Map<String, byte[]> files() {
    final Map<String, byte[]> copy = new TreeMap<>();
    files.forEach((name, content) -> copy.put(name, content.clone()));
    return copy;
  }

  /**
   * @throws UnsupportedOperationException for Python, whose wrapper is not available yet
   */
  public static ExecutorWrapper of(ScriptLanguage language) {
    return switch (language) {
      case JAVASCRIPT -> {
        final Map<String, byte[]> files = new TreeMap<>();
        JAVASCRIPT_FILES.forEach(name -> files.put(name, resource("node/" + name)));
        yield new ExecutorWrapper(language, files);
      }
      case PYTHON ->
          throw new UnsupportedOperationException(
              "The Python executor wrapper is not available yet");
    };
  }

  /**
   * This wrapper with the given files added, such as a provider's adapter or dependency manifest.
   */
  public ExecutorWrapper with(Map<String, byte[]> moreFiles) {
    final Map<String, byte[]> all = new TreeMap<>(files);
    all.putAll(moreFiles);
    return new ExecutorWrapper(language, all);
  }

  /**
   * SHA-256, in hex, over the provider's runtime identifier and every file, name and content. It
   * changes with any packaged file, so an upgrade gives a new executor, never an update in place.
   *
   * @param runtime the provider's runtime identifier, such as {@code nodejs22.x}
   */
  public String digest(String runtime) {
    try {
      final var sha256 = MessageDigest.getInstance("SHA-256");
      update(sha256, runtime.getBytes(StandardCharsets.UTF_8));
      for (var file : files.entrySet()) {
        update(sha256, file.getKey().getBytes(StandardCharsets.UTF_8));
        update(sha256, file.getValue());
      }
      return HexFormat.of().formatHex(sha256.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  /**
   * {@code <prefix><py|js>-<12 hex of the digest>}: every runtime instance of a cluster agrees on
   * it, and it is new for every wrapper.
   */
  public String executorName(String prefix, String runtime) {
    final var shortName =
        switch (language) {
          case PYTHON -> "py";
          case JAVASCRIPT -> "js";
        };
    return prefix + shortName + "-" + digest(runtime).substring(0, NAME_DIGEST_LENGTH);
  }

  // Length-prefixed, so that moving bytes between a name and a content changes the digest.
  private static void update(MessageDigest digest, byte[] bytes) {
    digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
    digest.update(bytes);
  }

  private static byte[] resource(String path) {
    try (InputStream in = ExecutorWrapper.class.getResourceAsStream(ROOT + path)) {
      if (in == null) {
        throw new IllegalStateException("Missing executor wrapper file " + path);
      }
      return in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
