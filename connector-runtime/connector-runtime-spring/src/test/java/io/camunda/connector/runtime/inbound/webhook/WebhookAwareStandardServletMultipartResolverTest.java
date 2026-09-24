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
package io.camunda.connector.runtime.inbound.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class WebhookAwareStandardServletMultipartResolverTest {

  private final WebhookAwareStandardServletMultipartResolver resolver =
      new WebhookAwareStandardServletMultipartResolver("/api");

  @Test
  void doesNotResolveNonFormMultipartWebhookRequest() {
    var request = request("/api/inbound/webhook", "multipart/related; boundary=x");

    assertThat(resolver.isMultipart(request)).isFalse();
  }

  @Test
  void leavesMultipartFormDataWebhookRequestForTheController() {
    var request = request("/api/inbound/webhook", "multipart/form-data; boundary=x");

    assertThat(resolver.isMultipart(request)).isFalse();
  }

  @Test
  void preservesDefaultMultipartResolutionOutsideWebhookPaths() {
    var request = request("/api/management/import", "multipart/related; boundary=x");

    assertThat(resolver.isMultipart(request)).isTrue();
  }

  private static MockHttpServletRequest request(String path, String contentType) {
    var request = new MockHttpServletRequest();
    request.setRequestURI(path);
    request.setContentType(contentType);
    return request;
  }
}
