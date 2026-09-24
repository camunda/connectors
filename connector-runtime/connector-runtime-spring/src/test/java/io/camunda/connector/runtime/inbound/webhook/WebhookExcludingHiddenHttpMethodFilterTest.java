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

class WebhookExcludingHiddenHttpMethodFilterTest {

  private final WebhookExcludingHiddenHttpMethodFilter filter =
      new WebhookExcludingHiddenHttpMethodFilter("");

  @Test
  void shouldNotFilterLegacyWebhookPath() {
    assertThat(filter.shouldNotFilter(postFormRequest("/inbound/myPath"))).isTrue();
  }

  @Test
  void shouldNotFilterExactWebhookRootPath() {
    assertThat(filter.shouldNotFilter(postFormRequest("/inbound"))).isTrue();
  }

  @Test
  void shouldNotFilterPhysicalTenantScopedWebhookPath() {
    assertThat(filter.shouldNotFilter(postFormRequest("/inbound/physical-tenant/tenant/myPath")))
        .isTrue();
  }

  @Test
  void shouldNotFilterWebhookPathUnderNonRootServletMapping() {
    var filterUnderServletPath = new WebhookExcludingHiddenHttpMethodFilter("/api");
    assertThat(filterUnderServletPath.shouldNotFilter(postFormRequest("/api/inbound/myPath")))
        .isTrue();
  }

  @Test
  void shouldNotFilterWebhookPathUnderServletMappingWithTrailingSlash() {
    // spring.mvc.servlet.path=/api/ (trailing slash) is a supported form, normalized by Spring
    // Boot's own WebMvcProperties.Servlet#getServletPrefix() the same way as "/api".
    var filterUnderServletPath = new WebhookExcludingHiddenHttpMethodFilter("/api/");
    assertThat(filterUnderServletPath.shouldNotFilter(postFormRequest("/api/inbound/myPath")))
        .isTrue();
  }

  @Test
  void shouldNotFilterWebhookPathWithExplicitRootServletMapping() {
    var filterUnderRootServletPath = new WebhookExcludingHiddenHttpMethodFilter("/");
    assertThat(filterUnderRootServletPath.shouldNotFilter(postFormRequest("/inbound/myPath")))
        .isTrue();
  }

  @Test
  void shouldStillFilterNonWebhookPathWithFormUrlEncodedPost() {
    // Everywhere else in the app, ordinary HiddenHttpMethodFilter behavior must be unaffected: a
    // POST with a form-urlencoded body outside /inbound/** must still be inspected for _method.
    assertThat(filter.shouldNotFilter(postFormRequest("/some/other/endpoint"))).isFalse();
  }

  private static MockHttpServletRequest postFormRequest(String uri) {
    var request = new MockHttpServletRequest();
    request.setMethod("POST");
    request.setRequestURI(uri);
    request.setContentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
    return request;
  }
}
