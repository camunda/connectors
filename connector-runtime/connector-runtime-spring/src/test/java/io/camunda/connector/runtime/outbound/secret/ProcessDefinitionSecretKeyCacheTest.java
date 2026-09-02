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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.fetch.ProcessDefinitionGetXmlRequest;
import io.camunda.connector.runtime.outbound.secret.SecretKeyCache.SecretKeyContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessDefinitionSecretKeyCacheTest {

  private static final long PROCESS_DEF_KEY = 1L;

  @Mock private CamundaClient camundaClient;
  @Mock private ProcessDefinitionGetXmlRequest xmlRequest;

  private ProcessDefinitionSecretKeyCache secretKeyCache;

  @BeforeEach
  void setUp() {
    // A real Caffeine cache, not a mock: exercises the actual get(key, Function) contract this
    // class now relies on.
    secretKeyCache =
        new ProcessDefinitionSecretKeyCache(camundaClient, Caffeine.newBuilder().build());
    when(camundaClient.newProcessDefinitionGetXmlRequest(anyLong())).thenReturn(xmlRequest);
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
  void getSecretKeys_taskWithoutTemplate_returnsItsOwnSecrets() throws IOException {
    // zeebe:modelerTemplate records how an element was authored, not what it is. A task that
    // declares a secret in its own input mapping must be allow-listed for it whether or not it was
    // built by applying an element template in Modeler.
    when(xmlRequest.execute()).thenReturn(loadBpmn("outbound-no-template.bpmn"));

    var keys = secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "plain-task"));

    assertThat(keys).containsExactly("SECRET_X");
  }

  @Test
  void getSecretKeys_adHocSubProcessAndChild_returnsEachElementsOwnSecrets() throws IOException {
    when(xmlRequest.execute()).thenReturn(loadBpmn("outbound-adhoc-subprocess.bpmn"));

    var subProcessKeys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "agent-subprocess"));
    var childKeys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "child-task"));

    assertThat(subProcessKeys).containsExactly("ADHOC_SECRET");
    assertThat(childKeys).containsExactly("CHILD_SECRET");
  }

  @Test
  void getSecretKeys_nestedEmbeddedSubProcessesAndGrandchild_returnsEachElementsOwnSecrets()
      throws IOException {
    // Verifies the fix generalizes beyond AdHocSubProcess: a plain (embedded) SubProcess can
    // itself be a connector host, at any nesting depth, same as the ad-hoc flavor.
    when(xmlRequest.execute()).thenReturn(loadBpmn("outbound-nested-embedded-subprocess.bpmn"));

    var outerKeys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "outer-subprocess"));
    var innerKeys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "inner-subprocess"));
    var grandchildKeys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "grandchild-task"));

    assertThat(outerKeys).containsExactly("OUTER_SECRET");
    assertThat(innerKeys).containsExactly("INNER_SECRET");
    assertThat(grandchildKeys).containsExactly("GRANDCHILD_SECRET");
  }

  @Test
  void getSecretKeys_messageIntermediateThrowEventAndEndEvent_returnsEachElementsOwnSecrets()
      throws IOException {
    when(xmlRequest.execute()).thenReturn(loadBpmn("outbound-message-events.bpmn"));

    var throwEventKeys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "send-message-throw"));
    var endEventKeys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "send-message-end"));

    assertThat(throwEventKeys).containsExactly("THROW_EVENT_SECRET");
    assertThat(endEventKeys).containsExactly("END_EVENT_SECRET");
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
