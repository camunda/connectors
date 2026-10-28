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
package io.camunda.connector.runtime.managedcode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The deployable executor of one provider and one language: the trusted wrapper, never a user
 * script. Its digest covers the files and the runtime, so a changed wrapper gets a new function
 * name: an executor is never updated in place.
 *
 * @param files file name to content, in a fixed order so that the package is deterministic
 * @param entrypoint what the provider invokes, for example {@code aws_handler.handler}
 * @param runtime the runtime identifier of the provider, for example {@code python3.13}
 */
record ExecutorPackage(
    ScriptLanguage language, Map<String, byte[]> files, String entrypoint, String runtime) {

  /** Default prefix of provider resource names, before the prefix of a cluster is configured. */
  static final String DEFAULT_NAME_PREFIX = "camunda-ms-";

  /** Characters of the wrapper digest in a function name. */
  static final int NAME_DIGEST_LENGTH = 12;

  /**
   * {@code py-} or {@code js-} plus the digest. Cloud Run function ids have at most 63 characters,
   * Lambda function names 64, so the prefix of a cluster may have up to 48.
   */
  static final int NAME_SUFFIX_LENGTH = 3 + NAME_DIGEST_LENGTH;

  private static final String ROOT = "/managed-code-executor/";
  private static final long FIXED_ZIP_TIME = 315_532_800_000L;

  ExecutorPackage {
    files = Collections.unmodifiableMap(new LinkedHashMap<>(files));
  }

  /** AWS Lambda: the shared core and child runner, with the Lambda handler. */
  static ExecutorPackage aws(ScriptLanguage language) {
    final Map<String, byte[]> files = core(language);
    return switch (language) {
      case PYTHON -> {
        files.put("aws_handler.py", resource("python/aws_handler.py"));
        yield new ExecutorPackage(language, files, "aws_handler.handler", "python3.13");
      }
      case JAVASCRIPT -> {
        files.put("index.mjs", resource("node/aws_index.mjs"));
        yield new ExecutorPackage(language, files, "index.handler", "nodejs22.x");
      }
    };
  }

  /**
   * Google Cloud Run functions: the shared core and child runner, with a Functions Framework app.
   */
  static ExecutorPackage gcp(ScriptLanguage language) {
    final Map<String, byte[]> files = core(language);
    return switch (language) {
      case PYTHON -> {
        files.put("main.py", resource("python/gcp_main.py"));
        files.put("requirements.txt", resource("python/gcp_requirements.txt"));
        yield new ExecutorPackage(language, files, "executor", "python313");
      }
      case JAVASCRIPT -> {
        files.put("index.js", resource("node/gcp_index.js"));
        files.put("package.json", resource("node/gcp_package.json"));
        yield new ExecutorPackage(language, files, "executor", "nodejs22");
      }
    };
  }

  /** The local provider: the shared core and child runner, with a one-call adapter. */
  static ExecutorPackage local(ScriptLanguage language) {
    final Map<String, byte[]> files = core(language);
    return switch (language) {
      case PYTHON -> {
        files.put("local_call.py", resource("python/local_call.py"));
        yield new ExecutorPackage(language, files, "local_call.py", "python3");
      }
      case JAVASCRIPT -> {
        files.put("local_call.mjs", resource("node/local_call.mjs"));
        yield new ExecutorPackage(language, files, "local_call.mjs", "nodejs");
      }
    };
  }

  /** SHA-256 over the runtime and every file, name and content, as a hex string. */
  String digest() {
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
   * {@code <prefix><py|js>-<12 hex of the digest>}: deterministic, so every runtime instance of a
   * cluster agrees on it, and new for every wrapper version.
   */
  String functionName(String prefix) {
    return prefix + shortName(language) + "-" + digest().substring(0, NAME_DIGEST_LENGTH);
  }

  /** Deterministic zip: fixed entry order and timestamps, so its hash depends on content only. */
  byte[] zip() {
    final var bytes = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(bytes)) {
      for (var file : files.entrySet()) {
        final var entry = new ZipEntry(file.getKey());
        entry.setTime(FIXED_ZIP_TIME);
        zip.putNextEntry(entry);
        zip.write(file.getValue());
        zip.closeEntry();
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return bytes.toByteArray();
  }

  private static String shortName(ScriptLanguage language) {
    return switch (language) {
      case PYTHON -> "py";
      case JAVASCRIPT -> "js";
    };
  }

  private static Map<String, byte[]> core(ScriptLanguage language) {
    final Map<String, byte[]> files = new LinkedHashMap<>();
    switch (language) {
      case PYTHON -> {
        files.put("executor_core.py", resource("python/executor_core.py"));
        files.put("child.py", resource("python/child.py"));
      }
      case JAVASCRIPT -> {
        files.put("executor_core.mjs", resource("node/executor_core.mjs"));
        files.put("child.mjs", resource("node/child.mjs"));
      }
    }
    return files;
  }

  private static byte[] resource(String path) {
    try (InputStream stream = ExecutorPackage.class.getResourceAsStream(ROOT + path)) {
      if (stream == null) {
        throw new IllegalStateException("Missing managed-script executor resource: " + path);
      }
      return stream.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Length-prefixed so that moving bytes between adjacent fields changes the digest. */
  private static void update(MessageDigest sha256, byte[] field) {
    sha256.update(ByteBuffer.allocate(Integer.BYTES).putInt(field.length).array());
    sha256.update(field);
  }
}
