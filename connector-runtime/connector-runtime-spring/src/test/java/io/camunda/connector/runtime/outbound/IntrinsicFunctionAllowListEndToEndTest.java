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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.fetch.ProcessDefinitionGetXmlRequest;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.TestObjectMapperSupplier;
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

  // The actual conditional shape GitHub's shipped template uses: the "then" branch declares
  // createGithubAppInstallationToken as a literal, but the "else" branch is a bare reference to
  // "githubPat" -- bound by a separate, earlier zeebe:input -- rather than a fixed fallback shape.
  private static final String GITHUB_AUTH_MODEL_XML_WITH_UNVERIFIABLE_ELSE_BRANCH =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                        xmlns:zeebe="http://camunda.org/schema/zeebe/1.0"
                        id="defs" targetNamespace="http://bpmn.io/schema/bpmn">
        <bpmn:process id="proc" isExecutable="true">
          <bpmn:serviceTask id="http_task" name="HTTP">
            <bpmn:extensionElements>
              <zeebe:ioMapping>
                <zeebe:input source="=githubPat" target="githubPat" />
                <zeebe:input
                    source="=if githubAuthType = &quot;github_app&quot; then {&quot;camunda.function.type&quot;:&quot;createGithubAppInstallationToken&quot;,&quot;params&quot;:[key]} else githubPat"
                    target="authentication.token" />
              </zeebe:ioMapping>
            </bpmn:extensionElements>
          </bpmn:serviceTask>
        </bpmn:process>
      </bpmn:definitions>
      """;

  // Mirrors the Microsoft 365 Mail connector's actual sendMail attachments binding shape: the
  // "body" input's FEEL source is a nested context/list literal that declares base64 three levels
  // under the input's own target ("body" -> "message" -> "attachments" (array) -> "contentBytes"),
  // not at "body" itself.
  private static final String NESTED_ATTACHMENT_MODEL_XML =
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
                    source="={&quot;message&quot;:{&quot;attachments&quot;: for document in attachments return {&quot;contentBytes&quot;:{&quot;camunda.function.type&quot;:&quot;base64&quot;,&quot;params&quot;:[document]}}}}"
                    target="body" />
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
    return contextFor(modelXml, variablesJson, mode, ConnectorsObjectMapperSupplier.getCopy());
  }

  private JobHandlerContext contextFor(
      String modelXml,
      String variablesJson,
      IntrinsicFunctionAllowListMode mode,
      ObjectMapper objectMapper) {
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

    return new JobHandlerContext(
        job,
        mock(SecretProvider.class),
        mock(ValidationProvider.class),
        mock(DocumentFactory.class),
        objectMapper,
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
  void theGithubStyleDeclaredCallIsNotBlockedByTheGateAndActuallyDispatches() {
    // Unlike the other tests in this class, this one uses TestObjectMapperSupplier.INSTANCE — the
    // same shape ConnectorsAutoConfiguration wires in production, with
    // DefaultIntrinsicFunctionExecutor
    // live — rather than a bare mapper with no document module at all. The model declares base64 at
    // "body", and the job's own variables already carry the discriminator there (mirroring what
    // Zeebe's own FEEL evaluation of the model's "={"camunda.function.type":"base64",...}" produces
    // at runtime): this proves the full chain end to end, not just that the gate doesn't throw.
    String variablesJson =
        "{\"body\": {\"camunda.function.type\":\"base64\",\"params\":[\"Hello World\"]}}";
    var context =
        contextFor(
            GITHUB_STYLE_MODEL_XML,
            variablesJson,
            IntrinsicFunctionAllowListMode.ENABLED,
            TestObjectMapperSupplier.INSTANCE);

    var result = context.bindVariables(TargetType.class);

    assertThat(result.body()).isEqualTo("SGVsbG8gV29ybGQ=");
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
  void aCallDeclaredOnlyInABranchWithAnUnverifiableSiblingIsNoLongerRefused() {
    // security-testing-findings#275 follow-up on PR #8991 (camunda/connectors#9046): the
    // cross-branch check this test used to prove closed a gap has been reverted, as a deliberate,
    // accepted relaxation -- it broke the shipped GitHub template's own auth-mode conditional
    // (this exact shape) without protecting against a real case, since createLink -- the one
    // function whose params can reference another tenant's/process's data -- is not declared by
    // any shipped template, and createGithubAppInstallationToken only mints a credential scoped
    // to whatever the declaring branch's own params already name. The gate now grants this
    // declaration regardless of the "else" branch's shape, so binding no longer throws.
    String variablesJson =
        """
        {"authentication": {"token":
          {"camunda.function.type":"createGithubAppInstallationToken",
           "params":["attacker-key","attacker-app","attacker-installation"]}}}
        """;
    var context = contextFor(GITHUB_AUTH_MODEL_XML_WITH_UNVERIFIABLE_ELSE_BRANCH, variablesJson);

    var result = context.bindVariables(AuthTargetType.class);

    // The bare mapper this test uses (no document module registered) never dispatches the
    // function regardless of the gate -- it just binds the discriminator node as an ordinary
    // nested map, which is enough to prove the gate itself no longer refuses this shape.
    assertThat(result.authentication())
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
        .containsKey("token");
  }

  @Test
  void aCallDeclaredSeveralLevelsInsideAContextAndListLiteralIsNotBlockedByTheGate() {
    // Regression for the Microsoft 365 Mail connector's real sendMail-with-attachments shape:
    // NESTED_ATTACHMENT_MODEL_XML declares base64 at "body" -> "message" -> "attachments" (array,
    // adds no segment) -> "contentBytes", not at the zeebe:input's own target ("body") alone.
    // Before
    // computing that full nested path, the allow-list recorded every declaration at its input's
    // bare
    // target, so this exact legitimate shape was wrongly refused.
    String variablesJson =
        """
        {"body": {"message": {"attachments": [
          {"contentBytes": {"camunda.function.type":"base64","params":["ZG9jdW1lbnQ="]}}
        ]}}}
        """;
    var context = contextFor(NESTED_ATTACHMENT_MODEL_XML, variablesJson);

    var result = context.bindVariables(TargetType.class);

    assertThat(result.body()).isNotNull();
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
