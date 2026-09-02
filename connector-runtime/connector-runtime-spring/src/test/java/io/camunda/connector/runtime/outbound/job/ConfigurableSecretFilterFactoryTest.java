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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.client.CamundaClient;
import io.camunda.connector.runtime.core.secret.SecretFilterFactory.SecretFilterContext;
import io.camunda.connector.runtime.outbound.job.ConfigurableSecretFilterFactory.SecretFilterMode;
import io.camunda.connector.runtime.outbound.secret.ProcessDefinitionSecretKeyCache;
import io.camunda.connector.runtime.outbound.secret.SecretKeyCache;
import io.camunda.connector.runtime.outbound.secret.SecretKeyCache.SecretKeyContext;
import java.util.List;
import java.util.Map;
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
  void create_lax_withSecretKeys_restrictsFilterToList() {
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
  void create_strict_withSecretKeys_restrictsFilterToList() {
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

  @Test
  void create_strict_whenCacheThrows_messageIdentifiesTheFailureWithoutLeakingTheCauseText() {
    // The incident must be actionable (element ID, process-definition key, exception class) but
    // must never carry the cause's own message: a client/parser exception message can echo
    // response-body content.
    when(secretKeyCache.getSecretKeys(any()))
        .thenThrow(new RuntimeException("Operate returned 404 for process definition 42"));
    var factory = new ConfigurableSecretFilterFactory(SecretFilterMode.STRICT, secretKeyCache);

    var filter = factory.create(CONTEXT);

    assertThatThrownBy(() -> filter.isAllowed("ANY_SECRET"))
        .hasMessageContaining(ELEMENT_ID)
        .hasMessageContaining(String.valueOf(PROCESS_DEF_KEY))
        .hasMessageContaining(RuntimeException.class.getName())
        .hasMessageNotContaining("Operate returned 404 for process definition 42");
  }

  @Test
  void create_strict_whenCauseIsWrappedSeveralLayersDeep_messageIdentifiesTheRootCause() {
    // A client can wrap the real failure in its own generic exception type before it ever reaches
    // this code. A single getCause() would still return that generic, non-discriminating type;
    // the message must walk to the actual root.
    var rootCause = new java.net.ConnectException("Connection refused");
    when(secretKeyCache.getSecretKeys(any()))
        .thenThrow(
            new RuntimeException(
                "outer",
                new RuntimeException("sdk wrapper", new RuntimeException("mid", rootCause))));
    var factory = new ConfigurableSecretFilterFactory(SecretFilterMode.STRICT, secretKeyCache);

    var filter = factory.create(CONTEXT);

    assertThatThrownBy(() -> filter.isAllowed("ANY_SECRET"))
        .hasMessageContaining(java.net.ConnectException.class.getName())
        .hasMessageNotContaining("Connection refused");
  }

  @Test
  void
      create_strict_whenProcessDefinitionLookupFailsThroughARealCache_messageIdentifiesTheFailureWithoutLeakingTheCauseText() {
    // Goes through a real Cache#get(key, mappingFunction) -- unlike the mock above, this
    // reproduces the actual path in production.
    assertMessageIdentifiesTheFailureWithoutLeakingTheCauseText(Caffeine.newBuilder().build());
  }

  @Test
  void
      create_strict_whenProcessDefinitionLookupFailsThroughADisabledCache_messageIdentifiesTheFailureWithoutLeakingTheCauseText() {
    assertMessageIdentifiesTheFailureWithoutLeakingTheCauseText(
        Caffeine.newBuilder().maximumSize(0).build());
  }

  private void assertMessageIdentifiesTheFailureWithoutLeakingTheCauseText(
      Cache<Long, Map<String, List<String>>> cache) {
    var camundaClient = mock(CamundaClient.class);
    when(camundaClient.newProcessDefinitionGetXmlRequest(PROCESS_DEF_KEY))
        .thenThrow(new RuntimeException("Operate returned 404 for process definition 42"));
    SecretKeyCache realSecretKeyCache = new ProcessDefinitionSecretKeyCache(camundaClient, cache);
    var factory = new ConfigurableSecretFilterFactory(SecretFilterMode.STRICT, realSecretKeyCache);

    var filter = factory.create(CONTEXT);

    assertThatThrownBy(() -> filter.isAllowed("ANY_SECRET"))
        .hasMessageContaining(ELEMENT_ID)
        .hasMessageContaining(String.valueOf(PROCESS_DEF_KEY))
        .hasMessageContaining(RuntimeException.class.getName())
        .hasMessageNotContaining("could not be loaded using")
        .hasMessageNotContaining("Operate returned 404 for process definition 42");
  }
}
