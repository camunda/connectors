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
package io.camunda.connector.generator.dsl;

import static io.camunda.connector.generator.java.annotation.BpmnType.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.generator.dsl.DropdownProperty.DropdownChoice;
import io.camunda.connector.generator.dsl.ElementTemplate.Metadata;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeInput;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeTaskHeader;
import io.camunda.connector.generator.dsl.PropertyCondition.AllMatch;
import io.camunda.connector.generator.dsl.PropertyCondition.Equals;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.json.ElementTemplateModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

public class ElementTemplateSerializationTest {

  private final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new ElementTemplateModule());

  @Test
  void serializationTest() throws Exception {
    // given
    var elementTemplate =
        ElementTemplate.builderForOutbound()
            .id("io.camunda.connector.Template.v1")
            .type("io.camunda:template:1")
            .name("Template: Some Function")
            .appliesTo(Set.of(TASK))
            .elementType(SERVICE_TASK)
            .version(1)
            .documentationRef(
                "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/available-connectors-overview/")
            .description("Describe this connector")
            .metadata(new Metadata(new String[] {"foo", "bar"}))
            .propertyGroups(
                PropertyGroup.builder()
                    .id("authentication")
                    .label("Authentication")
                    .properties(
                        StringProperty.builder()
                            .label("Username")
                            .description("The username for authentication.")
                            .constraints(PropertyConstraints.builder().notEmpty(true).build())
                            .feel(FeelMode.optional)
                            .binding(new ZeebeInput("authentication.user")),
                        StringProperty.builder()
                            .label("Token")
                            .description("The token for authentication.")
                            .constraints(PropertyConstraints.builder().notEmpty(true).build())
                            .feel(FeelMode.staticFeel)
                            .binding(new ZeebeInput("authentication.token")))
                    .build(),
                PropertyGroup.builder()
                    .id("compose")
                    .label("Compose")
                    .properties(
                        DropdownProperty.builder()
                            .choices(List.of(new DropdownChoice("message", "Compose a message")))
                            .label("Type")
                            .description("The type of message to compose")
                            .binding(new ZeebeInput("compose.type")),
                        DropdownProperty.builder()
                            .choices(
                                List.of(
                                    new DropdownChoice("With topic", "withTopic"),
                                    new DropdownChoice("Without topic", "withoutTopic")))
                            .label("With topic?")
                            .binding(new ZeebeInput("compose.withTopic")),
                        TextProperty.builder()
                            .label("Topic")
                            .description("The topic of the message")
                            .feel(FeelMode.optional)
                            .binding(new ZeebeInput("compose.topic"))
                            .condition(
                                new AllMatch(
                                    new Equals("compose.type", "message"),
                                    new Equals("compose.withTopic", "withTopic"))),
                        TextProperty.builder()
                            .label("Message")
                            .feel(FeelMode.optional)
                            .binding(new ZeebeInput("message"))
                            .condition(new PropertyCondition.Equals("compose.type", "message"))
                            .constraints(PropertyConstraints.builder().notEmpty(true).build()))
                    .build(),
                PropertyGroup.builder()
                    .id("output")
                    .label("Output Mapping")
                    .tooltip("Map the response to process variables")
                    .properties(
                        StringProperty.builder()
                            .label("Result Variable")
                            .description("Name of variable to store the response in")
                            .binding(new ZeebeTaskHeader("resultVariable")),
                        TextProperty.builder()
                            .label("Result Expression")
                            .description("Expression to map the response into process variables")
                            .feel(FeelMode.required)
                            .binding(new ZeebeTaskHeader("resultExpression")))
                    .build(),
                PropertyGroup.builder()
                    .id("errors")
                    .label("Error Handling")
                    .properties(
                        TextProperty.builder()
                            .label("Error Expression")
                            .description(
                                "Expression to handle errors. Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/use-connectors/\" target=\"_blank\">documentation</a>.")
                            .group("errors")
                            .feel(FeelMode.required)
                            .binding(new ZeebeTaskHeader("errorExpression")))
                    .build())
            .build();

    // when
    var jsonString = objectMapper.writeValueAsString(elementTemplate);

    // then
    var path = Path.of(ClassLoader.getSystemResource("test-element-template.json").toURI());
    var referenceJsonString = Files.readString(path);
    JSONAssert.assertEquals(referenceJsonString, jsonString, true);
  }
}
