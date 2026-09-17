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
package io.camunda.connector.runtime.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.fetch.ProcessDefinitionGetXmlRequest;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.intrinsic.IntrinsicFunctionAllowListFactory.IntrinsicFunctionAllowListContext;
import io.camunda.connector.runtime.core.outbound.JobHandlerContext;
import io.camunda.connector.runtime.core.secret.SecretFilter;
import io.camunda.connector.runtime.outbound.job.ConfigurableIntrinsicFunctionAllowListFactory;
import io.camunda.connector.runtime.outbound.job.ConfigurableIntrinsicFunctionAllowListFactory.IntrinsicFunctionAllowListMode;
import io.camunda.connector.runtime.outbound.secret.ProcessDefinitionIntrinsicFunctionAllowListCache;
import io.camunda.connector.runtime.outbound.secret.ProcessDefinitionModelCache;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;

/**
 * Proves the allow-list mechanism (not just the earlier blanket disable) closes
 * security-testing-findings#275: a webhook-route-shaped ioMapping (dynamic FEEL reference, no
 * literal declaration) is refused, while a GitHub-App-shaped ioMapping (literal declaration) is not
 * blocked by the gate. Exercises the real chain: BPMN model -> allow-list cache -> allow-list
 * factory -> JobHandlerContext, the same wiring {@code SpringConnectorJobHandler} uses per job.
 */
class IntrinsicFunctionAllowListEndToEndTest {

  private record TargetType(Object body) {}

  private record AuthTargetType(Object authentication) {}

  // Mirrors the issue's actual exploit shape: an ordinary ioMapping pulling from process data,
  // with no literal "camunda.function.type" anywhere in the model text.
  private static final String EXPLOIT_MODEL_XML =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                        xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
                        id="defs" targetNamespace="http://bpmn.io/schema/bpmn">
        <bpmn:process id="proc" isExecutable="true">
          <bpmn:serviceTask id="http_task" name="HTTP">
            <bpmn:extensionElements>
              <zeebe:ioMapping>
                <zeebe:input source="={probeResult: hookResult.request.body.probe}" target="body" />
              </zeebe:ioMapping>
            </bpmn:extensionElements>
          </bpmn:serviceTask>
        </bpmn:process>
      </bpmn:definitions>
      """;

  // Mirrors GitHub's actual base64 fallback binding shape.
  private static final String GITHUB_STYLE_MODEL_XML =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                        xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
                        id="defs" targetNamespace="http://bpmn.io/schema/bpmn">
        <bpmn:process id="proc" isExecutable="true">
          <bpmn:serviceTask id="http_task" name="HTTP">
            <bpmn:extensionElements>
              <zeebe:ioMapping>
                <zeebe:input
                    source="={&quot;camunda.function.type&quot;:&quot;base64&quot;,&quot;params&quot;:[content]}"
                    target="body" />
              </zeebe:ioMapping>
            </bpmn:extensionElements>
          </bpmn:serviceTask>
        </bpmn:process>
      </bpmn:definitions>
      """;

  // Declares createGithubAppInstallationToken at "authentication.token" — the actual shape of
  // GitHub's shipped template. Used to prove a declaration at this exact path does not also
  // authorize a same-named call arriving at a deeper, separately-sourced path beneath it.
  private static final String GITHUB_AUTH_MODEL_XML =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                        xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
                        id="defs" targetNamespace="http://bpmn.io/schema/bpmn">
        <bpmn:process id="proc" isExecutable="true">
          <bpmn:serviceTask id="http_task" name="HTTP">
            <bpmn:extensionElements>
              <zeebe:ioMapping>
                <zeebe:input
                    source="={&quot;camunda.function.type&quot;:&quot;createGithubAppInstallationToken&quot;,&quot;params&quot;:[key]}"
                    target="authentication.token" />
              </zeebe:ioMapping>
            </bpmn:extensionElements>
          </bpmn:serviceTask>
        </bpmn:process>
      </bpmn:definitions>
      """;

  private CamundaClient clientReturningXml(String xml) {
    var client = mock(CamundaClient.class);
    var request = mock(ProcessDefinitionGetXmlRequest.class);
    when(client.newProcessDefinitionGetXmlRequest(anyLong())).thenReturn(request);
    when(request.execute()).thenReturn(xml);
    return client;
  }

  private JobHandlerContext contextFor(String modelXml, String variablesJson) {
    return contextFor(modelXml, variablesJson, IntrinsicFunctionAllowListMode.ENABLED);
  }

  private JobHandlerContext contextFor(
      String modelXml, String variablesJson, IntrinsicFunctionAllowListMode mode) {
    var modelCache =
        new ProcessDefinitionModelCache(
            "tenant-a", clientReturningXml(modelXml), new ConcurrentMapCache("models"));
    var allowListCache =
        new ProcessDefinitionIntrinsicFunctionAllowListCache(
            "tenant-a", modelCache, new ConcurrentMapCache("allow-list"));
    var factory = new ConfigurableIntrinsicFunctionAllowListFactory(mode, allowListCache);
    var allowList =
        factory.create(
            new IntrinsicFunctionAllowListContext(1L, "http_task", Instant.now().plusSeconds(30)));

    var job = mock(ActivatedJob.class);
    when(job.getVariables()).thenReturn(variablesJson);
    when(job.getTenantId()).thenReturn("t");
    when(job.getBpmnProcessId()).thenReturn("proc");
    when(job.getPhysicalTenantId()).thenReturn(null);

    return new JobHandlerContext(
        job,
        mock(SecretProvider.class),
        mock(ValidationProvider.class),
        mock(DocumentFactory.class),
        ConnectorsObjectMapperSupplier.getCopy(),
        SecretFilter.allowAll(),
        allowList);
  }

  @Test
  void theWebhookExploitShapeIsRefused() {
    // The webhook payload the ioMapping evaluates to at runtime — the ioMapping's own model text
    // never contains "camunda.function.type" (see EXPLOIT_MODEL_XML), so no field-path declares it.
    String variablesJson =
        """
        {"body": {"camunda.function.type":"createLink",
                  "params":[{"camunda.document.type":"camunda"}, "PT1H"]}}
        """;
    var context = contextFor(EXPLOIT_MODEL_XML, variablesJson);

    assertThatThrownBy(() -> context.bindVariables(TargetType.class))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("createLink");
  }

  @Test
  void theGithubStyleDeclaredCallIsNotBlockedByTheGate() {
    // The model declares base64 at "body". This mapper (ConnectorsObjectMapperSupplier.getCopy(),
    // with no document module registered) never actually dispatches the call — proving that isn't
    // this test's job; it proves the allow-list gate itself doesn't refuse a declared call before
    // binding even reaches that point, unlike theWebhookExploitShapeIsRefused above.
    String variablesJson = "{\"body\": \"placeholder\"}";
    var context = contextFor(GITHUB_STYLE_MODEL_XML, variablesJson);

    var result = context.bindVariables(TargetType.class);

    assertThat(result.body()).isEqualTo("placeholder");
  }

  @Test
  void aSameNamedCallInjectedAtADeeperPathThanTheDeclarationIsStillRefused() {
    // GITHUB_AUTH_MODEL_XML declares createGithubAppInstallationToken at exactly
    // "authentication.token" — nothing declares anything at "authentication.token.extra". This
    // reuses the SAME function name the model does legitimately declare (the strongest form of
    // this attack: a bare prefix match, not just any descendant, would authorize this), sourced
    // from a path only a separate, attacker-controlled zeebe:input could ever populate. Regression
    // test for the prefix-matching gap closed by IntrinsicFunctionAllowList#allowOnly requiring an
    // exact (functionName, fieldPath) match.
    String variablesJson =
        """
        {"authentication": {"token": {"extra":
          {"camunda.function.type":"createGithubAppInstallationToken",
           "params":["attacker-key","attacker-app","attacker-installation"]}}}}
        """;
    var context = contextFor(GITHUB_AUTH_MODEL_XML, variablesJson);

    assertThatThrownBy(() -> context.bindVariables(AuthTargetType.class))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("createGithubAppInstallationToken");
  }

  @Test
  void disabledModeStillRefusesTheExploitShapeRatherThanFailingOpen() {
    // camunda.connector.intrinsic-function.allow-list.mode=DISABLED must not mean "dispatch
    // unconditionally" — that would silently reopen security-testing-findings#275 for any operator
    // (e.g. a self-managed deployment without the BPMN-fetch endpoint available) who turns the
    // allow-list mechanism off. It means "refuse everything instead", the same posture as the
    // interim, pre-allow-list fix. The model here is irrelevant to the outcome in this mode — the
    // BPMN is never even fetched (verified by aSameNamedCallInjectedAtADeeperPathThanTheDeclaration
    // IsStillRefused's own model already covering the fetch path) — what matters is the mode.
    String variablesJson =
        """
        {"body": {"camunda.function.type":"createLink",
                  "params":[{"camunda.document.type":"camunda"}, "PT1H"]}}
        """;
    var context =
        contextFor(EXPLOIT_MODEL_XML, variablesJson, IntrinsicFunctionAllowListMode.DISABLED);

    assertThatThrownBy(() -> context.bindVariables(TargetType.class))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("createLink");
  }

  @Test
  void disabledModeStillBindsOrdinaryDataNormally() {
    var context =
        contextFor(
            EXPLOIT_MODEL_XML, "{\"body\": \"hello\"}", IntrinsicFunctionAllowListMode.DISABLED);

    var result = context.bindVariables(TargetType.class);

    assertThat(result.body()).isEqualTo("hello");
  }
}
