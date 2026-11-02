/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript.aws;

import io.camunda.connector.managedscript.api.ExecutorWrapper;
import io.camunda.connector.managedscript.api.ScriptLanguage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The Lambda function of one language: the wrapper of ADR-0008 with the Lambda entry point, as a
 * zip package. Zip only: Lambda would pull a container image with its own identity, without
 * checking the cluster that calls it.
 *
 * <p>{@code runtime} is the Lambda runtime identifier, part of the digest, and {@code handler} the
 * entry point Lambda calls.
 */
record LambdaExecutor(ExecutorWrapper wrapper, String runtime, String handler) {

  private static final String ROOT = "/io/camunda/connector/managedscript/aws/";

  /** 1980-01-01, the earliest time of a zip entry: the package depends on its content only. */
  private static final long FIXED_ENTRY_TIME = 315_532_800_000L;

  static LambdaExecutor of(ScriptLanguage language) {
    return switch (language) {
      case PYTHON ->
          new LambdaExecutor(
              withEntryPoint(language, "lambda_handler.py", "python/lambda_handler.py"),
              "python3.13",
              "lambda_handler.handler");
      case JAVASCRIPT ->
          new LambdaExecutor(
              withEntryPoint(language, "index.mjs", "node/index.mjs"),
              "nodejs22.x",
              "index.handler");
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

  private static ExecutorWrapper withEntryPoint(
      ScriptLanguage language, String name, String resource) {
    try (InputStream in = LambdaExecutor.class.getResourceAsStream(ROOT + resource)) {
      if (in == null) {
        throw new IllegalStateException("Missing Lambda entry point " + resource);
      }
      return ExecutorWrapper.of(language).with(Map.of(name, in.readAllBytes()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
