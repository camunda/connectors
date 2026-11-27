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
package io.camunda.connector.http.rest.templates;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.feel.LocalFeelExpressionEvaluator;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.io.File;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The ServiceNow templates run on this connector: their query parameters are a FEEL expression that
 * the engine evaluates into the {@code queryParameters} input.
 */
class ServiceNowTemplateQueryParametersTest {

  private static final ObjectMapper MAPPER = ConnectorsObjectMapperSupplier.getCopy();
  private static final LocalFeelExpressionEvaluator FEEL = new LocalFeelExpressionEvaluator();

  private static String queryParametersExpression(String templateFile) throws Exception {
    JsonNode template =
        MAPPER.readTree(new File("../../servicenow/element-templates/" + templateFile));
    return StreamSupport.stream(template.get("properties").spliterator(), false)
        .filter(p -> p.path("binding").path("type").asText().equals("zeebe:input"))
        .filter(p -> p.path("binding").path("name").asText().equals("queryParameters"))
        .map(p -> p.get("value").asText())
        .findFirst()
        .orElseThrow(() -> new AssertionError("no queryParameters input in " + templateFile));
  }

  // The templates do not change the encoded query. ServiceNow decides what a space means: on a
  // text field a space after an operator is part of the value, and some operators contain one
  // (NOT LIKE, NOT IN).
  private static Stream<Arguments> typedQueries() {
    return Stream.of("servicenow-connector.json", "servicenow-incident-connector.json")
        .flatMap(template ->
            Stream.of(
                    "active=true^priority=1",
                    "short_description=Test",
                    "short_description= Test",
                    "short_descriptionLIKE mail",
                    "assignment_group.name=Service Desk^short_descriptionLIKEmail server",
                    "active = true ^ priority = 1",
                    "priority=1 ^OR priority=2",
                    " active=true ",
                    "priorityNOT IN1,5^short_descriptionNOT LIKEtest",
                    "active=true^ORDERBYDESC sys_created_on")
                .map(typed -> Arguments.of(template, typed)));
  }

  @ParameterizedTest
  @MethodSource("typedQueries")
  void encodedQueryIsSentAsTyped(String templateFile, String typed) throws Exception {
    Map<String, Object> result =
        FEEL.evaluate(queryParametersExpression(templateFile), Map.of("sysparm_query", typed));

    assertThat(result).containsEntry("sysparm_query", typed);
  }

  @ParameterizedTest
  @ValueSource(strings = {"servicenow-connector.json", "servicenow-incident-connector.json"})
  void fieldListStillDropsSpacesAfterCommas(String templateFile) throws Exception {
    Map<String, Object> result =
        FEEL.evaluate(
            queryParametersExpression(templateFile),
            Map.of("sysparm_fields", "number, short_description, state"));

    assertThat(result).containsEntry("sysparm_fields", "number,short_description,state");
  }

  @ParameterizedTest
  @ValueSource(strings = {"servicenow-connector.json", "servicenow-incident-connector.json"})
  void missingQueryStaysNull(String templateFile) throws Exception {
    Map<String, Object> result =
        FEEL.evaluate(queryParametersExpression(templateFile), Map.of("sysparm_limit", "10"));

    assertThat(result).containsEntry("sysparm_query", null).containsEntry("sysparm_limit", "10");
  }
}
