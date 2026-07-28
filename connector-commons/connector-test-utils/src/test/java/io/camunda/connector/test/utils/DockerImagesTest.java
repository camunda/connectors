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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
class DockerImagesTest {

  @SystemStub private EnvironmentVariables environment;

  @Test
  void returnsPropertyValueVerbatimWhenOverrideIsUnset() {
    environment.set(DockerImages.REGISTRY_OVERRIDE_ENV_VAR, null);

    assertThat(DockerImages.get("sample-image")).isEqualTo("sample/image:1.0");
  }

  @Test
  void returnsPropertyValueVerbatimWhenOverrideIsBlank() {
    environment.set(DockerImages.REGISTRY_OVERRIDE_ENV_VAR, "   ");

    assertThat(DockerImages.get("sample-image")).isEqualTo("sample/image:1.0");
  }

  @Test
  void prependsRegistryOverrideWhenSet() {
    environment.set(DockerImages.REGISTRY_OVERRIDE_ENV_VAR, "my-mirror.internal.example.com");

    assertThat(DockerImages.get("sample-image"))
        .isEqualTo("my-mirror.internal.example.com/sample/image:1.0");
  }

  @Test
  void stripsTrailingSlashFromRegistryOverride() {
    environment.set(DockerImages.REGISTRY_OVERRIDE_ENV_VAR, "my-mirror.internal.example.com/");

    assertThat(DockerImages.get("sample-image"))
        .isEqualTo("my-mirror.internal.example.com/sample/image:1.0");
  }

  @Test
  void returnsNullForUnknownKeyRegardlessOfOverride() {
    environment.set(DockerImages.REGISTRY_OVERRIDE_ENV_VAR, "my-mirror.internal.example.com");

    assertThat(DockerImages.get("does-not-exist")).isNull();
  }

  @Test
  void prependsRegistryOverrideEvenWhenEntryAlreadyHasARegistryHost() {
    environment.set(DockerImages.REGISTRY_OVERRIDE_ENV_VAR, "my-mirror.internal.example.com");

    // The override is applied unconditionally: an entry that already has an explicit registry
    // host (e.g. quay.io) does NOT have that host stripped/replaced, it is nested under the
    // override instead. This mirrors how registry mirrors/pull-through caches conventionally
    // address the original image path.
    assertThat(DockerImages.get("already-qualified-image"))
        .isEqualTo("my-mirror.internal.example.com/quay.io/foo/bar:1.0");
  }
}
