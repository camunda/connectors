/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript.gcp;

import io.camunda.connector.managedscript.api.ExecutorWrapper;
import io.camunda.connector.managedscript.api.ScriptLanguage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The Cloud Run function of one language: the wrapper of ADR-0008 with the Functions Framework
 * entry point and the manifest that pins the Functions Framework, as a zip source. The manifest is
 * part of the digest, so a new Functions Framework version gives a new executor.
 *
 * <p>{@code runtime} is the Cloud Run functions runtime identifier, part of the digest, and {@code
 * entryPoint} the function the Functions Framework serves.
 */
record CloudRunExecutor(ExecutorWrapper wrapper, String runtime, String entryPoint) {

  static final String ENTRY_POINT = "handler";

  private static final String ROOT = "/io/camunda/connector/managedscript/gcp/";

  /** 1980-01-01, the earliest time of a zip entry: the package depends on its content only. */
  private static final long FIXED_ENTRY_TIME = 315_532_800_000L;

  static CloudRunExecutor of(ScriptLanguage language) {
    return switch (language) {
      case PYTHON ->
          new CloudRunExecutor(
              withEntryPoint(
                  language,
                  Map.of(
                      "main.py", "python/main.py", "requirements.txt", "python/requirements.txt")),
              "python313",
              ENTRY_POINT);
      case JAVASCRIPT ->
          new CloudRunExecutor(
              withEntryPoint(
                  language,
                  Map.of("index.mjs", "node/index.mjs", "package.json", "node/package.json")),
              "nodejs22",
              ENTRY_POINT);
    };
  }

  ScriptLanguage language() {
    return wrapper.language();
  }

  /** {@code <prefix><py|js>-<12 hex of the digest>}. */
  String functionName(String prefix) {
    return wrapper.executorName(prefix, runtime);
  }

  String digest() {
    return wrapper.digest(runtime);
  }

  /** Deterministic: entries in name order, with a fixed time. */
  byte[] zip() {
    final var bytes = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(bytes)) {
      for (var file : wrapper.files().entrySet()) {
        final var entry = new ZipEntry(file.getKey());
        entry.setTime(FIXED_ENTRY_TIME);
        zip.putNextEntry(entry);
        zip.write(file.getValue());
        zip.closeEntry();
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return bytes.toByteArray();
  }

  /**
   * @param files name in the package to resource path
   */
  private static ExecutorWrapper withEntryPoint(
      ScriptLanguage language, Map<String, String> files) {
    final Map<String, byte[]> contents = new TreeMap<>();
    files.forEach((name, resource) -> contents.put(name, resource(resource)));
    return ExecutorWrapper.of(language).with(contents);
  }

  private static byte[] resource(String path) {
    try (InputStream in = CloudRunExecutor.class.getResourceAsStream(ROOT + path)) {
      if (in == null) {
        throw new IllegalStateException("Missing Cloud Run entry point file " + path);
      }
      return in.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
