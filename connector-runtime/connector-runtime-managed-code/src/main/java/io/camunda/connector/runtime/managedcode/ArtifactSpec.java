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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Everything a provider needs to build and deploy one script artifact.
 *
 * @param digest SHA-256 over script, wrapper, language, runtime, dependency manifest and build
 *     policy version; identical inputs always produce the same digest
 * @param deploymentName deterministic provider resource name, see {@link #deploymentName(Key)}
 */
public record ArtifactSpec(
    Key key,
    ScriptLanguage language,
    String runtime,
    String resourceName,
    byte[] script,
    Optional<byte[]> dependencies,
    String deploymentName) {

  /**
   * Version of the packaging rules. Bump it when the build output changes for identical inputs, so
   * that existing deployments are not reused for the new build.
   */
  static final String BUILD_POLICY_VERSION = "1";

  private static final String DEPLOYMENT_NAME_PREFIX = "camunda-ms-";
  private static final int DEPLOYMENT_NAME_HASH_LENGTH = 40;

  /** Deployment registry key; one provider deployment exists per key. */
  public record Key(String physicalTenantId, String tenantId, String provider, String digest) {}

  static ArtifactSpec create(
      String physicalTenantId,
      String tenantId,
      String provider,
      ScriptLanguage language,
      String runtime,
      String resourceName,
      byte[] script,
      Optional<byte[]> dependencies) {
    final var normalizedRuntime = runtime.strip().toLowerCase(Locale.ROOT);
    final var digest = digest(language, normalizedRuntime, script, dependencies);
    final var key = new Key(physicalTenantId, tenantId, provider, digest);
    return new ArtifactSpec(
        key, language, normalizedRuntime, resourceName, script, dependencies, deploymentName(key));
  }

  public String digest() {
    return key.digest();
  }

  static String digest(
      ScriptLanguage language, String runtime, byte[] script, Optional<byte[]> dependencies) {
    final var sha256 = sha256();
    update(sha256, BUILD_POLICY_VERSION.getBytes(StandardCharsets.UTF_8));
    update(sha256, language.language().getBytes(StandardCharsets.UTF_8));
    update(sha256, runtime.getBytes(StandardCharsets.UTF_8));
    update(sha256, language.wrapper());
    update(sha256, script);
    if (dependencies.isPresent()) {
      update(sha256, dependencies.get());
    } else {
      sha256.update(ByteBuffer.allocate(Integer.BYTES).putInt(-1).array());
    }
    return HexFormat.of().formatHex(sha256.digest());
  }

  /**
   * Derived from the whole registry key, not from the digest alone: identical scripts of two
   * tenants must not adopt each other's deployment, because cross-tenant reuse is not assumed.
   */
  static String deploymentName(Key key) {
    final var sha256 = sha256();
    update(sha256, key.physicalTenantId().getBytes(StandardCharsets.UTF_8));
    update(sha256, key.tenantId().getBytes(StandardCharsets.UTF_8));
    update(sha256, key.provider().getBytes(StandardCharsets.UTF_8));
    update(sha256, key.digest().getBytes(StandardCharsets.UTF_8));
    return DEPLOYMENT_NAME_PREFIX
        + HexFormat.of().formatHex(sha256.digest()).substring(0, DEPLOYMENT_NAME_HASH_LENGTH);
  }

  /** Length-prefixed so that moving bytes between adjacent fields changes the digest. */
  private static void update(MessageDigest sha256, byte[] field) {
    sha256.update(ByteBuffer.allocate(Integer.BYTES).putInt(field.length).array());
    sha256.update(field);
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }
}
