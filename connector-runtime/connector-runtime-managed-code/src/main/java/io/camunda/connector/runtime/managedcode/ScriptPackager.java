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
import java.util.HexFormat;

/**
 * Turns a script written for the Camunda contract, {@code execute(variables, context)}, into what a
 * provider invokes: a generated entrypoint that adapts the invocation of the provider to the
 * contract, next to the user script. The rest of the lifecycle, provisioning, invocation and
 * deletion, belongs to the {@link ManagedCodeProvider} of the same name.
 */
public interface ScriptPackager {

  /** Name of the {@link ManagedCodeProvider} this packager builds for. */
  String provider();

  /**
   * Version of the generated code, part of the artifact digest: a change to the entrypoint gives
   * every script a new digest, so its next job provisions a new deployment.
   */
  String buildVersion();

  /**
   * @throws ProvisioningException when the artifact cannot be packaged for the provider, for
   *     example an unsupported runtime
   */
  ScriptPackage packageFor(ArtifactSpec artifact);

  /** Reads a resource of the packager, such as a generated entrypoint. */
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
