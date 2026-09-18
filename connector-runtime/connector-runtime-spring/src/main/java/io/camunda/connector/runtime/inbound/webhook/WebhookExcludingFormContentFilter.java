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

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.web.filter.FormContentFilter;

/**
 * Spring Boot registers a {@code FormContentFilter} by default (unless {@code
 * spring.mvc.formcontent.filter.enabled=false}) that parses {@code
 * application/x-www-form-urlencoded} bodies for PUT/PATCH/DELETE requests — fully into memory, with
 * no size limit — in a servlet {@link jakarta.servlet.Filter} that runs before {@code
 * DispatcherServlet}, i.e. before path resolution, the webhook rate limit, and {@code
 * InboundWebhookRestController}'s own body-size guard. Both webhook routes accept PUT and DELETE,
 * so without this override, a request in that shape would be fully buffered regardless of the
 * target path being registered, bypassing every guard this fix adds.
 *
 * <p>Registering a bean of this type (in {@link WebhookConnectorConfiguration}) replaces Spring
 * Boot's own, since it is only auto-configured when no {@code FormContentFilter} bean already
 * exists. Skipping this filter for webhook paths is also correct independent of the security
 * concern: webhook connectors need the untouched raw body (e.g. for HMAC verification), the same
 * reason {@link InboundWebhookRestController} already avoids {@code getParameterMap()} for POST.
 *
 * <p>Implements {@link Ordered} directly (rather than extending Spring Boot's {@code
 * OrderedFormContentFilter}, which {@code spring-boot-servlet} isn't on this module's classpath
 * for) with the same order value Spring Boot's own auto-configured filter uses, so this runs at the
 * same point in the filter chain that it would have.
 */
public class WebhookExcludingFormContentFilter extends FormContentFilter implements Ordered {

  private static final String WEBHOOK_PATH_PREFIX = "/inbound/";

  /** Matches {@code OrderedFormContentFilter.DEFAULT_ORDER} in spring-boot-servlet. */
  private static final int ORDER = -9900;

  @Override
  public int getOrder() {
    return ORDER;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
    return request.getRequestURI().startsWith(WEBHOOK_PATH_PREFIX)
        || super.shouldNotFilter(request);
  }
}
