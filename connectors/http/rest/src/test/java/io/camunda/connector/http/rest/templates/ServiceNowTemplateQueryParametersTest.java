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

  @ParameterizedTest
  @ValueSource(strings = {"servicenow-connector.json", "servicenow-incident-connector.json"})
  void encodedQueryKeepsSpacesInsideValues(String templateFile) throws Exception {
    Map<String, Object> result =
        FEEL.evaluate(
            queryParametersExpression(templateFile),
            Map.of(
                "sysparm_query",
                "assignment_group.name=Service Desk^short_descriptionLIKEmail server"));

    assertThat(result)
        .containsEntry(
            "sysparm_query", "assignment_group.name=Service Desk^short_descriptionLIKEmail server");
  }

  // Spaces typed around the ^ separator (and after ^OR, ^NQ, ^ORDERBY) or around the whole query
  // are not part of any value, and earlier template versions removed them.
  private static Stream<Arguments> spacesAroundSeparators() {
    return Stream.of("servicenow-connector.json", "servicenow-incident-connector.json")
        .flatMap(template ->
            Stream.of(
                Arguments.of(template, "active=true ^ priority=1", "active=true^priority=1"),
                Arguments.of(template, "priority=1 ^OR priority=2", "priority=1^ORpriority=2"),
                Arguments.of(template, "priority=1 ^ OR priority=2", "priority=1^ORpriority=2"),
                Arguments.of(template, "active=true ^NQ state=1", "active=true^NQstate=1"),
                Arguments.of(template, "active=true ^ORDERBY number", "active=true^ORDERBYnumber"),
                Arguments.of(
                    template,
                    "active=true^ORDERBYDESC sys_created_on",
                    "active=true^ORDERBYDESCsys_created_on"),
                Arguments.of(template, " active=true ", "active=true"),
                Arguments.of(
                    template,
                    "assignment_group.name=Service Desk ^ short_descriptionLIKEmail server",
                    "assignment_group.name=Service Desk^short_descriptionLIKEmail server")));
  }

  @ParameterizedTest
  @MethodSource("spacesAroundSeparators")
  void encodedQueryDropsSpacesAroundSeparators(String templateFile, String typed, String sent)
      throws Exception {
    Map<String, Object> result =
        FEEL.evaluate(queryParametersExpression(templateFile), Map.of("sysparm_query", typed));

    assertThat(result).containsEntry("sysparm_query", sent);
  }

  // Spaces typed around = != < > <= >= are removed, wherever they are in the query, as earlier
  // template versions did. The other spaces of a value stay ("Service Desk").
  private static Stream<Arguments> spacesAroundComparisonOperators() {
    return Stream.of("servicenow-connector.json", "servicenow-incident-connector.json")
        .flatMap(template ->
            Stream.of(
                Arguments.of(template, "active = true", "active=true"),
                Arguments.of(template, "priority != 1", "priority!=1"),
                Arguments.of(template, "priority < 3", "priority<3"),
                Arguments.of(template, "priority > 3", "priority>3"),
                Arguments.of(template, "priority <= 3", "priority<=3"),
                Arguments.of(template, "priority >= 3", "priority>=3"),
                Arguments.of(template, "active = true ^ priority = 1", "active=true^priority=1"),
                Arguments.of(
                    template,
                    "assignment_group.name = Service Desk",
                    "assignment_group.name=Service Desk"),
                Arguments.of(
                    template,
                    "sys_created_on > 2026-01-01 00:00:00",
                    "sys_created_on>2026-01-01 00:00:00")));
  }

  @ParameterizedTest
  @MethodSource("spacesAroundComparisonOperators")
  void encodedQueryDropsSpacesAroundComparisonOperators(
      String templateFile, String typed, String sent) throws Exception {
    Map<String, Object> result =
        FEEL.evaluate(queryParametersExpression(templateFile), Map.of("sysparm_query", typed));

    assertThat(result).containsEntry("sysparm_query", sent);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "active=true^ORDERBYnumber",
        "active=true^EQ",
        "short_descriptionLIKEreport OR summary^active=true",
        "assignment_group.name=Service Desk^ORDERBYDESCsys_created_on"
      })
  void encodedQueryWithoutSpacesAroundSeparatorsIsSentAsTyped(String typed) throws Exception {
    for (String templateFile :
        new String[] {"servicenow-connector.json", "servicenow-incident-connector.json"}) {
      Map<String, Object> result =
          FEEL.evaluate(queryParametersExpression(templateFile), Map.of("sysparm_query", typed));

      assertThat(result).containsEntry("sysparm_query", typed);
    }
  }

  // Some encoded query operators contain spaces. The examples are from the operator table of
  // addEncodedQuery() in ServiceNow's docs:
  // https://github.com/ServiceNow/ServiceNowDocs/blob/brazil/markdown/api-reference/MobileGlideRecordScopedAPI.md
  @ParameterizedTest
  @ValueSource(
      strings = {
        "short_descriptionNOT LIKEtest",
        "descriptionDOES NOT CONTAINwifi",
        "priorityNOT IN1,5",
        "active=true^priorityNOT IN1,5^short_descriptionNOT LIKEtest"
      })
  void operatorsWithSpacesAreSentAsTyped(String typed) throws Exception {
    for (String templateFile :
        new String[] {"servicenow-connector.json", "servicenow-incident-connector.json"}) {
      Map<String, Object> result =
          FEEL.evaluate(queryParametersExpression(templateFile), Map.of("sysparm_query", typed));

      assertThat(result).containsEntry("sysparm_query", typed);
    }
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
