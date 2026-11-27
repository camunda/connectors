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
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The UiPath and Blue Prism templates build parts of the request (body, URL, OAuth token endpoint)
 * in hidden FEEL expressions that read other inputs of the template.
 */
class TemplateHiddenExpressionInputsTest {

  private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");

  // The Modeler writes no input mapping for an optional property that is left empty, so a hidden
  // expression that reads it gets the process variable of the same name instead. A property that
  // is not optional is always mapped, without a source when it is empty, and Zeebe evaluates that
  // to null.
  @ParameterizedTest
  @ValueSource(
      strings = {
        "uipath/element-templates/uipath-connector.json",
        "blue-prism/element-templates/blue-prism-connector.json"
      })
  void inputsReadByHiddenExpressionsAreNotOptional(String templateFile) throws Exception {
    List<JsonNode> inputs = inputs(templateFile);
    List<String> optional = new ArrayList<>();

    for (JsonNode expression : hiddenExpressions(inputs)) {
      for (JsonNode input : inputsReadBy(expression, inputs)) {
        if (input.path("optional").asBoolean()) {
          optional.add(name(input) + " (read by " + name(expression) + ")");
        }
      }
    }

    assertThat(optional).isEmpty();
  }

  // An expression sees only the inputs that are mapped before it, so an input that a hidden
  // expression reads has to be defined above it. By default Zeebe evaluates all "authentication.*"
  // inputs as one nested context, where the first of them stands: an input read by one of them
  // has to be defined above the first "authentication.*" input.
  @ParameterizedTest
  @ValueSource(
      strings = {
        "uipath/element-templates/uipath-connector.json",
        "blue-prism/element-templates/blue-prism-connector.json"
      })
  void inputsReadByHiddenExpressionsAreDefinedAboveThem(String templateFile) throws Exception {
    List<JsonNode> inputs = inputs(templateFile);
    List<String> definedBelow = new ArrayList<>();

    for (JsonNode expression : hiddenExpressions(inputs)) {
      JsonNode evaluatedAt =
          name(expression).contains(".")
              ? inputs.stream()
                  .filter(input -> variable(input).equals(variable(expression)))
                  .findFirst()
                  .orElseThrow()
              : expression;
      for (JsonNode input : inputsReadBy(expression, inputs)) {
        if (position(input, inputs) > position(evaluatedAt, inputs)) {
          definedBelow.add(
              name(input) + " (read by " + name(expression) + ", below " + name(evaluatedAt) + ")");
        }
      }
    }

    assertThat(definedBelow).isEmpty();
  }

  /** The {@code zeebe:input} properties of a template, in template order. */
  private static List<JsonNode> inputs(String templateFile) throws Exception {
    JsonNode template =
        ConnectorsObjectMapperSupplier.getCopy().readTree(new File("../../" + templateFile));
    List<JsonNode> inputs = new ArrayList<>();
    for (JsonNode property : template.path("properties")) {
      if (property.path("binding").path("type").asText().equals("zeebe:input")) {
        inputs.add(property);
      }
    }
    return inputs;
  }

  private static List<JsonNode> hiddenExpressions(List<JsonNode> inputs) {
    List<JsonNode> expressions =
        inputs.stream()
            .filter(input -> input.path("type").asText().equals("Hidden"))
            .filter(input -> input.path("value").asText().startsWith("="))
            .toList();
    assertThat(expressions).isNotEmpty();
    return expressions;
  }

  /**
   * The other inputs whose variable the FEEL expression of a hidden input names: as a whole word,
   * outside string literals and not as a field of another value ({@code response.status}). The
   * variable names of the FEEL parser are not used, because they leave out a variable that is named
   * like a key of the context it is used in, as in {@code {"status": status}}. This is a text scan,
   * not a parser: it also counts an input that is named like a function or an unquoted context key
   * of the expression, so name such an input differently.
   */
  private static List<JsonNode> inputsReadBy(JsonNode expression, List<JsonNode> inputs) {
    String feel = STRING_LITERAL.matcher(expression.path("value").asText()).replaceAll("\"\"");
    return inputs.stream()
        .filter(input -> input != expression)
        .filter(input ->
            Pattern.compile("(?<![\\w.])" + Pattern.quote(variable(input)) + "(?!\\w)")
                .matcher(feel)
                .find())
        .toList();
  }

  private static String name(JsonNode input) {
    return input.path("binding").path("name").asText();
  }

  /** The variable an input writes: {@code authentication} for {@code authentication.type}. */
  private static String variable(JsonNode input) {
    return name(input).split("\\.")[0];
  }

  private static int position(JsonNode input, List<JsonNode> inputs) {
    for (int i = 0; i < inputs.size(); i++) {
      if (inputs.get(i) == input) {
        return i;
      }
    }
    throw new AssertionError("Not an input of the template: " + name(input));
  }
}
