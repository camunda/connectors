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
package io.camunda.connector.runtime.outbound.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.fetch.ProcessDefinitionGetXmlRequest;
import io.camunda.connector.runtime.outbound.secret.SecretKeyCache.SecretKeyContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;

@ExtendWith(MockitoExtension.class)
class ProcessDefinitionSecretKeyCacheTest {

  private static final long PROCESS_DEF_KEY = 1L;

  @Mock private CamundaClient camundaClient;
  @Mock private ProcessDefinitionGetXmlRequest xmlRequest;
  @Mock private Cache cache;

  private ProcessDefinitionSecretKeyCache secretKeyCache;

  @BeforeEach
  void setUp() throws Exception {
    secretKeyCache = new ProcessDefinitionSecretKeyCache(camundaClient, cache);
    when(camundaClient.newProcessDefinitionGetXmlRequest(anyLong())).thenReturn(xmlRequest);
    when(cache.get(anyLong(), any(Callable.class)))
        .thenAnswer(
            invocation -> {
              Callable<?> loader = invocation.getArgument(1);
              return loader.call();
            });
  }

  @Test
  void getSecretKeys_singleTaskWithSecrets_returnsExtractedKeys() throws IOException {
    when(xmlRequest.execute()).thenReturn(loadBpmn("outbound-with-secrets.bpmn"));

    var keys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "service-task-1"));

    assertThat(keys).containsExactlyInAnyOrder("API_KEY", "MY_TOKEN");
  }

  @Test
  void getSecretKeys_multipleTasksWithSecrets_returnsOnlyKeysForRequestedElement()
      throws IOException {
    when(xmlRequest.execute()).thenReturn(loadBpmn("outbound-multiple-tasks-with-secrets.bpmn"));

    var alphaKeys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "task-alpha"));
    var betaKeys = secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "task-beta"));

    assertThat(alphaKeys).containsExactly("SECRET_ALPHA");
    assertThat(betaKeys).containsExactlyInAnyOrder("SECRET_BETA", "SECRET_GAMMA");
  }

  @Test
  void getSecretKeys_taskWithNoSecrets_returnsEmptyList() throws IOException {
    when(xmlRequest.execute()).thenReturn(loadBpmn("outbound-no-secrets.bpmn"));

    var keys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "no-secrets-task"));

    assertThat(keys).isEmpty();
  }

  @Test
  void getSecretKeys_taskWithoutTemplate_excluded_returnsEmptyList() throws IOException {
    when(xmlRequest.execute()).thenReturn(loadBpmn("outbound-no-template.bpmn"));

    var keys = secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "plain-task"));

    assertThat(keys).isEmpty();
  }

  @Test
  void getSecretKeys_unknownElementId_returnsEmptyList() throws IOException {
    when(xmlRequest.execute()).thenReturn(loadBpmn("outbound-with-secrets.bpmn"));

    var keys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "nonexistent-task"));

    assertThat(keys).isEmpty();
  }

  private String loadBpmn(String fileName) throws IOException {
    try (var stream = getClass().getClassLoader().getResourceAsStream("bpmn/" + fileName)) {
      if (stream == null) {
        throw new IllegalArgumentException("BPMN resource not found: bpmn/" + fileName);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
