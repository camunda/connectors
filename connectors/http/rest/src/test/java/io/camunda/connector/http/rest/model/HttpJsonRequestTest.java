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
package io.camunda.connector.http.rest.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.http.base.model.auth.BearerAuthentication;
import org.junit.jupiter.api.Test;

/** Verifies the per-connector consumption of a bound authentication credential (configuration). */
class HttpJsonRequestTest {

  @Test
  void usesCredentialAuthenticationWhenBound() {
    var request = new HttpJsonRequest();
    request.setAuthentication(new BearerAuthentication("inline-token"));
    request.setAuthenticationConfiguration(
        new RestAuthenticationConfiguration(new BearerAuthentication("credential-token")));

    assertThat(request.getAuthentication()).isInstanceOf(BearerAuthentication.class);
    assertThat(((BearerAuthentication) request.getAuthentication()).token())
        .isEqualTo("credential-token");
  }

  @Test
  void fallsBackToInlineAuthenticationWhenNoCredential() {
    var request = new HttpJsonRequest();
    request.setAuthentication(new BearerAuthentication("inline-token"));

    assertThat(((BearerAuthentication) request.getAuthentication()).token())
        .isEqualTo("inline-token");
  }
}
