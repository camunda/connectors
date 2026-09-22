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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorInputException;
import java.util.List;
import org.junit.jupiter.api.Test;

class IntrinsicFunctionUtilTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private JsonNode json(String text) throws Exception {
    return MAPPER.readTree(text);
  }

  @Test
  void allowsAnUndeclaredTreeWithNoDiscriminatorAtAll() throws Exception {
    var tree = json("{\"body\": {\"a\": 1, \"b\": [1, 2, 3]}}");

    assertThatCode(
            () ->
                IntrinsicFunctionUtil.verifyAgainstAllowList(
                    tree, IntrinsicFunctionAllowList.allowNone()))
        .doesNotThrowAnyException();
  }

  @Test
  void refusesADiscriminatorNotInTheAllowList() throws Exception {
    var tree = json("{\"body\": {\"camunda.function.type\":\"createLink\"}}");

    assertThatThrownBy(
            () ->
                IntrinsicFunctionUtil.verifyAgainstAllowList(
                    tree, IntrinsicFunctionAllowList.allowNone()))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("createLink")
        .hasMessageContaining("body");
  }

  @Test
  void allowsADiscriminatorDeclaredAtItsExactPath() throws Exception {
    var tree = json("{\"body\": {\"camunda.function.type\":\"createLink\"}}");
    var allowList =
        IntrinsicFunctionAllowList.allowOnly(
            List.of(new AllowedIntrinsicFunction("createLink", List.of("body"))));

    assertThatCode(() -> IntrinsicFunctionUtil.verifyAgainstAllowList(tree, allowList))
        .doesNotThrowAnyException();
  }

  @Test
  void refusesADiscriminatorAllowedOnlyAtADifferentPath() throws Exception {
    var tree = json("{\"other\": {\"camunda.function.type\":\"createLink\"}}");
    var allowList =
        IntrinsicFunctionAllowList.allowOnly(
            List.of(new AllowedIntrinsicFunction("createLink", List.of("body"))));

    assertThatThrownBy(() -> IntrinsicFunctionUtil.verifyAgainstAllowList(tree, allowList))
        .isInstanceOf(ConnectorInputException.class);
  }

  @Test
  void findsADiscriminatorNestedInsideAnArray() throws Exception {
    var tree = json("{\"body\": [{\"camunda.function.type\":\"createLink\"}]}");

    assertThatThrownBy(
            () ->
                IntrinsicFunctionUtil.verifyAgainstAllowList(
                    tree, IntrinsicFunctionAllowList.allowNone()))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("createLink");
  }

  @Test
  void anArrayDoesNotPushAFurtherPathSegment() throws Exception {
    // An allow-list declaration for "body" (not "body.0" or similar) must cover a discriminator
    // sitting inside an array at that same path -- arrays never push a further segment, mirroring
    // ProcessDefinitionIntrinsicFunctionAllowListCache's own extraction.
    var tree = json("{\"body\": [{\"camunda.function.type\":\"createLink\"}]}");
    var allowList =
        IntrinsicFunctionAllowList.allowOnly(
            List.of(new AllowedIntrinsicFunction("createLink", List.of("body"))));

    assertThatCode(() -> IntrinsicFunctionUtil.verifyAgainstAllowList(tree, allowList))
        .doesNotThrowAnyException();
  }
}
