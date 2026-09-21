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
package io.camunda.connector.runtime.core.intrinsic;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorInputException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IntrinsicFunctionUtilTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private static Map<String, Object> exploitNode() {
    return Map.of(
        "camunda.function.type",
        "createLink",
        "params",
        List.of(Map.of("camunda.document.type", "camunda"), "PT1H"));
  }

  @Test
  void refusesACallNotInTheAllowList() {
    var tree = mapper.valueToTree(Map.of("body", exploitNode()));

    assertThatThrownBy(
            () ->
                IntrinsicFunctionUtil.verifyAgainstAllowList(
                    tree, IntrinsicFunctionAllowList.allowNone()))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("createLink")
        .hasMessageContaining("body");
  }

  @Test
  void permitsACallTheAllowListDeclaresAtTheSameFieldPath() {
    var tree = mapper.valueToTree(Map.of("body", exploitNode()));
    var allowList =
        IntrinsicFunctionAllowList.allowOnly(
            List.of(new AllowedIntrinsicFunction("createLink", List.of("body"))));

    assertThatCode(() -> IntrinsicFunctionUtil.verifyAgainstAllowList(tree, allowList))
        .doesNotThrowAnyException();
  }

  @Test
  void refusesACallDeclaredOnlyAtADifferentFieldPath() {
    var tree = mapper.valueToTree(Map.of("body", exploitNode()));
    var allowList =
        IntrinsicFunctionAllowList.allowOnly(
            List.of(new AllowedIntrinsicFunction("createLink", List.of("b64content"))));

    assertThatThrownBy(() -> IntrinsicFunctionUtil.verifyAgainstAllowList(tree, allowList))
        .isInstanceOf(ConnectorInputException.class);
  }

  @Test
  void findsANestedCallAtItsOwnPath() {
    var nested = Map.of("outer", Map.of("inner", exploitNode()));
    var tree = mapper.valueToTree(nested);

    assertThatThrownBy(
            () ->
                IntrinsicFunctionUtil.verifyAgainstAllowList(
                    tree, IntrinsicFunctionAllowList.allowNone()))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("outer")
        .hasMessageContaining("inner");
  }

  @Test
  void findsACallInsideAnArray() {
    var tree = mapper.valueToTree(Map.of("items", List.of(exploitNode())));

    assertThatThrownBy(
            () ->
                IntrinsicFunctionUtil.verifyAgainstAllowList(
                    tree, IntrinsicFunctionAllowList.allowNone()))
        .isInstanceOf(ConnectorInputException.class);
  }

  @Test
  void plainDocumentReferenceIsNotMistakenForAFunctionCall() {
    var tree =
        mapper.valueToTree(
            Map.of("value", Map.of("camunda.document.type", "camunda", "documentId", "d")));

    assertThatCode(
            () ->
                IntrinsicFunctionUtil.verifyAgainstAllowList(
                    tree, IntrinsicFunctionAllowList.allowNone()))
        .doesNotThrowAnyException();
  }

  @Test
  void treeWithNoFunctionCallAtAllPassesEvenWithAnEmptyAllowList() {
    var tree = mapper.valueToTree(Map.of("status", "ok", "count", 3));

    assertThatCode(
            () ->
                IntrinsicFunctionUtil.verifyAgainstAllowList(
                    tree, IntrinsicFunctionAllowList.allowNone()))
        .doesNotThrowAnyException();
  }
}
