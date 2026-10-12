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

import static io.camunda.connector.e2e.agenticai.aiagent.AgentTestFixtures.AI_AGENT_TASK_V2_ELEMENT_TEMPLATE_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.e2e.BpmnFile;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;

class RealProviderFixtureTest {

  private static final Map<String, Class<?>> CAPABILITY_SUITES =
      Map.of(
          RealProviderCapabilityTags.CORE, RealProviderCoreE2ETestIT.class,
          RealProviderCapabilityTags.STRUCTURED_OUTPUT, RealProviderStructuredOutputE2ETestIT.class,
          RealProviderCapabilityTags.REASONING, RealProviderReasoningE2ETestIT.class,
          RealProviderCapabilityTags.PROMPT_CACHING, RealProviderPromptCachingE2ETestIT.class,
          RealProviderCapabilityTags.MULTIMODAL, RealProviderMultimodalE2ETestIT.class,
          RealProviderCapabilityTags.DOCUMENT_TOOL_CALL_RESULTS, DocumentToolCallResultsIT.class);

  @Test
  void shouldBindRequiredCiCapabilitiesToExecutableSuites() throws IOException {
    var objectMapper = ConnectorsObjectMapperSupplier.getCopy();
    final var registryResource = "ai-agent-cpt/registry.json";
    final JsonNode registry;
    try (var input =
        Objects.requireNonNull(
            getClass().getClassLoader().getResourceAsStream(registryResource),
            "Missing " + registryResource)) {
      registry = objectMapper.readTree(input);
    }
    Set<String> requiredCapabilities = new HashSet<>();
    registry
        .required("requiredCiCapabilities")
        .elements()
        .forEachRemaining(
            capabilities ->
                capabilities
                    .elements()
                    .forEachRemaining(tag -> requiredCapabilities.add(tag.asText())));

    assertThat(CAPABILITY_SUITES.keySet())
        .containsExactlyInAnyOrderElementsOf(requiredCapabilities);

    var failsafeConfiguration = Files.readString(Path.of(System.getProperty("basedir"), "pom.xml"));
    CAPABILITY_SUITES.forEach(
        (tag, suite) -> {
          assertThat(suite.getAnnotation(Tag.class))
              .as("%s capability tag", suite.getSimpleName())
              .isNotNull()
              .extracting(Tag::value)
              .isEqualTo(tag);
          assertThat(suite.getDeclaredMethods())
              .as("%s executable parameterized scenarios", suite.getSimpleName())
              .anyMatch(
                  method ->
                      method.isAnnotationPresent(ParameterizedTest.class)
                          && !method.isAnnotationPresent(Disabled.class));
          assertThat(
                  suite.getSimpleName().endsWith("E2ETestIT")
                      || failsafeConfiguration.contains(
                          "<include>**/" + suite.getSimpleName() + ".java</include>"))
              .as("%s Failsafe inclusion", suite.getSimpleName())
              .isTrue();
        });
  }

  @Test
  void shouldExposeOnlyScenarioRelevantTools() throws IOException {
    assertThat(serviceTaskIds("real-provider-api-smoke.bpmn"))
        .containsExactly("Lookup_Classified_Fact");
    assertThat(serviceTaskIds("real-provider-multi-tool.bpmn"))
        .containsExactlyInAnyOrder("Lookup_Classified_Fact", "Lookup_Access_Authorization");
    assertThat(serviceTaskIds("real-provider-no-tools.bpmn")).containsExactly("AI_Agent");
  }

  @Test
  void shouldApplyVertexConfigurationToNoToolTaskFixture(@TempDir File tempDir) throws Exception {
    var vertexProvider =
        RealProviderApiSmokeSupport.providerCatalog()
            .filter(
                provider -> provider.label().equals("google-gemini-vertex-ai-v2/gemini-3.8-flash"))
            .findFirst()
            .orElseThrow();
    var template =
        RealProviderApiSmokeSupport.configureBaseTemplate(AI_AGENT_TASK_V2_ELEMENT_TEMPLATE_PATH);
    vertexProvider.properties().forEach(template::property);
    template
        .property("data.response.format.type", "json")
        .property("data.response.format.schema", "={type:\"object\"}")
        .property("data.response.format.schemaName", "DirectFact");

    var templateFile = template.writeTo(new File(tempDir, "template.json"));
    var bpmnFile =
        new File(
            Objects.requireNonNull(
                    getClass().getClassLoader().getResource("real-provider-no-tools.bpmn"))
                .toURI());

    var model =
        new BpmnFile(bpmnFile).apply(templateFile, "AI_Agent", new File(tempDir, "applied.bpmn"));
    var agentTask = (ServiceTask) model.getModelElementById("AI_Agent");

    assertThat(agentTask.getSingleExtensionElement(ZeebeTaskDefinition.class).getType())
        .isEqualTo("io.camunda.agenticai:aiagent:task:2");
  }

  private Iterable<String> serviceTaskIds(String resource) throws IOException {
    try (var input =
        Objects.requireNonNull(
            getClass().getClassLoader().getResourceAsStream(resource), "Missing " + resource)) {
      return Bpmn.readModelFromStream(input).getModelElementsByType(ServiceTask.class).stream()
          .map(ServiceTask::getId)
          .toList();
    }
  }
}
