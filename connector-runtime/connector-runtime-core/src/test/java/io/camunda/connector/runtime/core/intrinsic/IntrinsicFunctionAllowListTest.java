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

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.runtime.core.intrinsic.IntrinsicFunctionAllowList.Call;
import java.util.List;
import org.junit.jupiter.api.Test;

class IntrinsicFunctionAllowListTest {

  @Test
  void allowAllPermitsAnything() {
    var allowList = IntrinsicFunctionAllowList.allowAll();

    assertThat(allowList.isAllowed(new Call("createLink", List.of("body")))).isTrue();
  }

  @Test
  void allowNoneRefusesEverything() {
    var allowList = IntrinsicFunctionAllowList.allowNone();

    assertThat(allowList.isAllowed(new Call("base64", List.of("b64content")))).isFalse();
  }

  @Test
  void allowOnlyPermitsExactNameAndPath() {
    var allowList =
        IntrinsicFunctionAllowList.allowOnly(
            List.of(new AllowedIntrinsicFunction("base64", List.of("b64content"))));

    assertThat(allowList.isAllowed(new Call("base64", List.of("b64content")))).isTrue();
    assertThat(allowList.isAllowed(new Call("createLink", List.of("b64content")))).isFalse();
    assertThat(allowList.isAllowed(new Call("base64", List.of("body")))).isFalse();
  }

  @Test
  void allowOnlyDoesNotMatchAPathThatExtendsTheDeclaredOne() {
    // Deliberately unlike Secret's field-path semantics (see class javadoc): a declaration at
    // "authentication.token" must not also authorize "authentication.token.extra" — that deeper
    // path can only be populated by some other, separately-sourced zeebe:input, which is exactly
    // the injection this class exists to refuse.
    var allowList =
        IntrinsicFunctionAllowList.allowOnly(
            List.of(
                new AllowedIntrinsicFunction(
                    "createGithubAppInstallationToken", List.of("authentication", "token"))));

    assertThat(
            allowList.isAllowed(
                new Call(
                    "createGithubAppInstallationToken",
                    List.of("authentication", "token", "extra"))))
        .isFalse();
  }

  @Test
  void allowOnlyWithEmptyListDeniesEverything() {
    var allowList = IntrinsicFunctionAllowList.allowOnly(List.of());

    assertThat(allowList.isAllowed(new Call("base64", List.of("b64content")))).isFalse();
  }
}
