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
package io.camunda.connector.e2e.agenticai.e2e;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.camunda.connector.e2e.agenticai.aiagent.AgentTestFixtures.AI_AGENT_SUB_PROCESS_V2_ELEMENT_TEMPLATE_PATH;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.e2e.agenticai.CamundaDocumentTestConfiguration;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class},
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=false",
      "camunda.connector.polling.enabled=false",
      "camunda.connector.agenticai.tools.process-definition.cache.enabled=false",
      "camunda.connector.agenticai.aiagent.chat-model.api.default-timeout=PT2M",
      "logging.level.io.camunda.connector.agenticai=TRACE"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
@EnabledIfEnvironmentVariable(named = "RUN_NATIVE_LLM_E2E", matches = "true")
@Import(CamundaDocumentTestConfiguration.class)
@WireMockTest
@Tag(RealProviderCapabilityTags.MULTIMODAL)
class RealProviderMultimodalE2ETestIT extends RealProviderApiSmokeSupport {

  @BeforeEach
  void clearDocumentStore() {
    InMemoryDocumentStore.INSTANCE.clear();
  }

  @AfterEach
  void clearDocumentStoreAfterTest() {
    InMemoryDocumentStore.INSTANCE.clear();
  }

  private static final String DOC_DIR = "document-tool-call-results/";
  private static final String DOC_PROJECT_LAUNCH = DOC_DIR + "project-launch.pdf";
  private static final String DOC_HEADCOUNT_REPORT = DOC_DIR + "headcount-report.pdf";
  private static final String DOC_AUTHOR_INFO = DOC_DIR + "author-info.pdf";
  private static final String DOCUMENT_BPMN_RESOURCE = "classpath:document-tool-call-results.bpmn";
  private static final String DOCUMENT_PROCESS_ID = "CPT_Document_Tool_Call_Results";

  @ParameterizedTest(name = "{0}", allowZeroInvocations = true)
  @MethodSource("providersWithMultimodalUserMessage")
  void documentInUserMessageIsReadByModel(ProviderConfig provider, WireMockRuntimeInfo wireMock) {
    stubPdfDownloads();

    final var systemPrompt =
        "You are a document analyst. A document is attached directly to the user's message. "
            + "Answer using only that attached document and do not call any tools. Always "
            + "quote specific facts, numbers, dates, and names found in the document.";

    // Reuses the document BPMN (which downloads downloadUrls into `downloadedFiles` before the
    // agent) but routes the single downloaded PDF into the user message instead of a tool result,
    // so this exercises the user-message multimodal path rather than the tool-result path.
    var model =
        buildModel(
            provider,
            AI_AGENT_SUB_PROCESS_V2_ELEMENT_TEMPLATE_PATH,
            DOCUMENT_BPMN_RESOURCE,
            systemPrompt,
            template -> template.property("data.userPrompt.documents", "=downloadedFiles"));

    var instance =
        startAgent(
            model,
            DOCUMENT_PROCESS_ID,
            systemPrompt,
            Map.of(
                "userPrompt",
                "What is the internal project code name mentioned in the attached document? "
                    + "Quote it exactly.",
                "downloadUrls",
                List.of(wireMock.getHttpBaseUrl() + "/" + DOC_PROJECT_LAUNCH)));

    assertResponseTextContains(instance, "Zypherion");
  }

  // ---------------------------------------------------------------------------

  private void stubPdfDownloads() {
    for (var doc : List.of(DOC_PROJECT_LAUNCH, DOC_HEADCOUNT_REPORT, DOC_AUTHOR_INFO)) {
      stubFor(
          get(urlPathEqualTo("/" + doc))
              .willReturn(
                  aResponse().withBodyFile(doc).withHeader("Content-Type", "application/pdf")));
    }
  }
}
