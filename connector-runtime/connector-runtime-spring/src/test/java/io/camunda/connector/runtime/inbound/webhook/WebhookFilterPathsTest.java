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

class WebhookFilterPathsTest {

  @Test
  void normalizesNullToEmpty() {
    assertThat(WebhookFilterPaths.normalizeServletPath(null)).isEqualTo("");
  }

  @Test
  void normalizesUnsetToEmpty() {
    assertThat(WebhookFilterPaths.normalizeServletPath("")).isEqualTo("");
  }

  @Test
  void normalizesExplicitRootMappingToEmpty() {
    assertThat(WebhookFilterPaths.normalizeServletPath("/")).isEqualTo("");
  }

  @Test
  void leavesAPlainPrefixUnchanged() {
    assertThat(WebhookFilterPaths.normalizeServletPath("/api")).isEqualTo("/api");
  }

  @Test
  void stripsATrailingSlash() {
    // Regression test: spring.mvc.servlet.path=/api/ is a supported form (Spring Boot's
    // WebMvcProperties.Servlet#getServletPrefix() strips it the same way); keeping it as a
    // literal prefix would strip the leading slash too when matched against a request path,
    // turning "/api/inbound/foo" into "inbound/foo" and breaking the match.
    assertThat(WebhookFilterPaths.normalizeServletPath("/api/")).isEqualTo("/api");
  }

  @Test
  void stripsARawWildcardServletMappingPattern() {
    // Also a supported form: the raw servlet-mapping pattern rather than a plain prefix.
    assertThat(WebhookFilterPaths.normalizeServletPath("/api/*")).isEqualTo("/api");
  }
}
