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
package io.camunda.connector.test.utils;

import java.io.IOException;
import java.util.Properties;

/**
 * Resolves Docker image references declared in a module's {@code docker-images.properties} for use
 * by Testcontainers-based tests.
 *
 * <p>By default {@link #get(String)} returns the property value verbatim, which Testcontainers then
 * pulls straight from the registry embedded in that reference (typically Docker Hub). If the
 * environment variable named by {@link #REGISTRY_OVERRIDE_ENV_VAR} is set to a non-blank value, it
 * is prepended as a registry host to every resolved image reference, so that a single environment
 * variable can redirect all test image pulls to an internal mirror/pull-through cache without
 * touching any test code or {@code docker-images.properties} file. The prefix is applied
 * unconditionally — an entry that already carries an explicit registry host (e.g. {@code
 * quay.io/keycloak/keycloak:26.5}) is prefixed too, rather than having its existing host replaced;
 * this matches how registry mirrors/pull-through caches are conventionally addressed (the full
 * original path becomes a sub-path of the mirror). See {@code
 * docs/adr/ADR-0006-localstack-test-image-strategy.md} for the motivating context (this mechanism
 * does not, by itself, imply that such a mirror exists).
 */
public class DockerImages {

  /**
   * When set to a non-blank value, this environment variable's value is prepended (as {@code
   * <value>/<image>}) to every image reference returned by {@link #get(String)}.
   */
  public static final String REGISTRY_OVERRIDE_ENV_VAR = "CONNECTORS_TEST_IMAGE_REGISTRY";

  private static final Properties PROPERTIES = new Properties();

  static {
    try {
      PROPERTIES.load(
          DockerImages.class.getClassLoader().getResourceAsStream("docker-images.properties"));
    } catch (IOException e) {
      throw new RuntimeException("Failed to load docker images from properties file", e);
    }
  }

  public static String get(String key) {
    String image = PROPERTIES.getProperty(key);
    if (image == null) {
      return null;
    }
    String registryOverride = System.getenv(REGISTRY_OVERRIDE_ENV_VAR);
    if (registryOverride == null || registryOverride.isBlank()) {
      return image;
    }
    String registry =
        registryOverride.endsWith("/")
            ? registryOverride.substring(0, registryOverride.length() - 1)
            : registryOverride;
    return registry + "/" + image;
  }
}
