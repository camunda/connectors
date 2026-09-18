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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

class WebhookExcludingFormContentFilterTest {

  private final WebhookExcludingFormContentFilter filter = new WebhookExcludingFormContentFilter();

  @Test
  void shouldNotFilterLegacyWebhookPath() throws Exception {
    assertThat(filter.shouldNotFilter(putFormRequest("/inbound/myPath"))).isTrue();
  }

  @Test
  void shouldNotFilterPhysicalTenantScopedWebhookPath() throws Exception {
    assertThat(filter.shouldNotFilter(putFormRequest("/inbound/physical-tenant/tenant/myPath")))
        .isTrue();
  }

  @Test
  void shouldNotFilterWebhookPathForDelete() throws Exception {
    var request = putFormRequest("/inbound/myPath");
    request.setMethod("DELETE");
    assertThat(filter.shouldNotFilter(request)).isTrue();
  }

  @Test
  void shouldStillFilterNonWebhookPathWithFormUrlEncodedPut() throws Exception {
    // Everywhere else in the app, ordinary FormContentFilter behavior must be unaffected: a
    // PUT/DELETE with a form-urlencoded body outside /inbound/** must still be parsed as before.
    assertThat(filter.shouldNotFilter(putFormRequest("/some/other/endpoint"))).isFalse();
  }

  private static MockHttpServletRequest putFormRequest(String uri) {
    var request = new MockHttpServletRequest();
    request.setMethod("PUT");
    request.setRequestURI(uri);
    request.setContentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
    return request;
  }
}
