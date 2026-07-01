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
package io.camunda.connector.runtime.outbound.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.connector.runtime.core.secret.SecretFilterFactory.SecretFilterContext;
import io.camunda.connector.runtime.outbound.job.ConfigurableSecretFilterFactory.SecretFilterMode;
import io.camunda.connector.runtime.outbound.secret.SecretKeyCache;
import io.camunda.connector.runtime.outbound.secret.SecretKeyCache.SecretKeyContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConfigurableSecretFilterFactoryTest {

  private static final long PROCESS_DEF_KEY = 42L;
  private static final String ELEMENT_ID = "service-task-1";
  private static final SecretFilterContext CONTEXT =
      new SecretFilterContext(PROCESS_DEF_KEY, ELEMENT_ID);
  private static final SecretKeyContext SECRET_KEY_CONTEXT =
      new SecretKeyContext(PROCESS_DEF_KEY, ELEMENT_ID);

  @Mock private SecretKeyCache secretKeyCache;

  @Test
  void create_disabled_allowsAllSecrets_withoutCacheInteraction() {
    var factory = new ConfigurableSecretFilterFactory(SecretFilterMode.DISABLED, secretKeyCache);

    var filter = factory.create(CONTEXT);

    assertThat(filter.isAllowed("ANY_SECRET")).isTrue();
    assertThat(filter.isAllowed("ANOTHER_SECRET")).isTrue();
    verifyNoInteractions(secretKeyCache);
  }

  @Test
  void create_lax_withSecretKeys_restrictesFilterToList() {
    when(secretKeyCache.getSecretKeys(SECRET_KEY_CONTEXT)).thenReturn(List.of("API_KEY", "TOKEN"));
    var factory = new ConfigurableSecretFilterFactory(SecretFilterMode.LAX, secretKeyCache);

    var filter = factory.create(CONTEXT);

    assertThat(filter.isAllowed("API_KEY")).isTrue();
    assertThat(filter.isAllowed("TOKEN")).isTrue();
    assertThat(filter.isAllowed("UNLISTED_SECRET")).isFalse();
  }

  @Test
  void create_lax_whenCacheThrows_allowsAll() {
    when(secretKeyCache.getSecretKeys(any())).thenThrow(new RuntimeException("fetch failed"));
    var factory = new ConfigurableSecretFilterFactory(SecretFilterMode.LAX, secretKeyCache);

    var filter = factory.create(CONTEXT);

    assertThat(filter.isAllowed("ANY_SECRET")).isTrue();
  }

  @Test
  void create_strict_withSecretKeys_restrictesFilterToList() {
    when(secretKeyCache.getSecretKeys(SECRET_KEY_CONTEXT)).thenReturn(List.of("API_KEY"));
    var factory = new ConfigurableSecretFilterFactory(SecretFilterMode.STRICT, secretKeyCache);

    var filter = factory.create(CONTEXT);

    assertThat(filter.isAllowed("API_KEY")).isTrue();
    assertThat(filter.isAllowed("UNLISTED_SECRET")).isFalse();
  }

  @Test
  void create_strict_whenCacheThrows_throwsIllegalArgumentException() {
    when(secretKeyCache.getSecretKeys(any())).thenThrow(new RuntimeException("fetch failed"));
    var factory = new ConfigurableSecretFilterFactory(SecretFilterMode.STRICT, secretKeyCache);

    var filter = factory.create(CONTEXT);

    assertThatThrownBy(() -> filter.isAllowed("ANY_SECRET"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Error retrieving secret keys");
  }
}
