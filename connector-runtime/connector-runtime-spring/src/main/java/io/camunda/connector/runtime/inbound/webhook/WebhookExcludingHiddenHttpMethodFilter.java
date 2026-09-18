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

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.web.filter.HiddenHttpMethodFilter;

/**
 * Spring Boot registers a {@code HiddenHttpMethodFilter} when {@code
 * spring.mvc.hiddenmethod.filter.enabled=true} (off by default) that, for POST requests, reads a
 * form-urlencoded body to look for a {@code _method} override parameter — fully into memory, with
 * no size limit — in a servlet {@link jakarta.servlet.Filter} that runs before {@code
 * DispatcherServlet}, i.e. before path resolution, the webhook rate limit, and {@code
 * InboundWebhookRestController}'s own body-size guard. The webhook POST route accepts
 * form-urlencoded bodies (e.g. for HMAC-signed webhook providers that use that content type), so
 * without this override, an operator who enables this property for unrelated endpoints would
 * unknowingly reopen the pre-dispatch buffering gap this fix closes for {@code /inbound/**}.
 *
 * <p>Registering a bean of this type (in {@link
 * io.camunda.connector.runtime.inbound.WebhookConnectorConfiguration}) replaces Spring Boot's own,
 * since it is only auto-configured when no {@code HiddenHttpMethodFilter} bean already exists.
 *
 * <p>Implements {@link Ordered} directly (rather than extending Spring Boot's {@code
 * OrderedHiddenHttpMethodFilter}, which {@code spring-boot-servlet} isn't on this module's
 * classpath for) with the same order value Spring Boot's own auto-configured filter uses, so this
 * runs at the same point in the filter chain that it would have.
 *
 * <p>See {@link WebhookFilterPaths} for the shared path-matching logic, also used by {@link
 * WebhookExcludingFormContentFilter}.
 */
public class WebhookExcludingHiddenHttpMethodFilter extends HiddenHttpMethodFilter
    implements Ordered {

  /** Matches {@code OrderedHiddenHttpMethodFilter.DEFAULT_ORDER} in spring-boot-servlet. */
  private static final int ORDER = -10000;

  private final String servletPath;

  /**
   * @param servletPath the configured {@code spring.mvc.servlet.path}, or {@code ""} if unset.
   */
  public WebhookExcludingHiddenHttpMethodFilter(String servletPath) {
    this.servletPath = WebhookFilterPaths.normalizeServletPath(servletPath);
  }

  @Override
  public int getOrder() {
    return ORDER;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return WebhookFilterPaths.isWebhookPath(request, servletPath);
  }
}
