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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A script artifact in the form a built-in provider deploys: the files of the function, with the
 * entrypoint and the runtime of the provider. Built by the packager of each built-in provider,
 * which places a generated entrypoint next to the user script; not part of the provider SPI.
 *
 * @param files file name to content, in a fixed order so that the package is deterministic
 * @param entrypoint what the provider invokes, for example {@code handler.lambda_handler}
 * @param runtime the runtime identifier of the provider, for example {@code python3.13}
 */
record ScriptPackage(Map<String, byte[]> files, String entrypoint, String runtime) {

  public ScriptPackage {
    files = Collections.unmodifiableMap(new LinkedHashMap<>(files));
  }

  /** Reads a resource of a packager, such as a generated entrypoint. */
  static byte[] resource(Class<?> owner, String path) {
    try (InputStream stream = owner.getResourceAsStream(path)) {
      if (stream == null) {
        throw new IllegalStateException("Missing managed-script resource: " + path);
      }
      return stream.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** SHA-256 over length-prefixed parts, as a hex string. */
  static String sha256(byte[]... parts) {
    try {
      final var sha256 = MessageDigest.getInstance("SHA-256");
      for (var part : parts) {
        sha256.update(ByteBuffer.allocate(Integer.BYTES).putInt(part.length).array());
        sha256.update(part);
      }
      return HexFormat.of().formatHex(sha256.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }
}
