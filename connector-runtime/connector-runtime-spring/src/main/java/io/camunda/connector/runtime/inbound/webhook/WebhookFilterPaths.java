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
import org.springframework.web.util.UrlPathHelper;

/**
 * Path-matching logic shared by {@link WebhookExcludingFormContentFilter} and {@link
 * WebhookExcludingHiddenHttpMethodFilter}: both need to recognize {@code /inbound/**} requests
 * before their respective wrapped filter would otherwise fully buffer the request body.
 */
final class WebhookFilterPaths {

  private static final String WEBHOOK_PATH = "/inbound";
  private static final String WEBHOOK_PATH_PREFIX = WEBHOOK_PATH + "/";
  private static final UrlPathHelper URL_PATH_HELPER = new UrlPathHelper();

  private WebhookFilterPaths() {}

  /**
   * {@code "/"} (an explicit root servlet mapping) is normalized to {@code ""}: {@code
   * getPathWithinApplication} already returns paths starting with {@code "/"}, so treating a
   * literal {@code "/"} as the prefix to strip would remove that leading slash from every path
   * (turning {@code "/inbound/foo"} into {@code "inbound/foo"}), breaking the match below for that
   * configuration.
   */
  static String normalizeServletPath(String servletPath) {
    return (servletPath == null || servletPath.equals("/")) ? "" : servletPath;
  }

  /**
   * Deliberately does <b>not</b> use {@code UrlPathHelper.getPathWithinServletMapping}: for the
   * common case (Spring Boot's default {@code DispatcherServlet} registration, mapped to the
   * servlet-spec "default servlet" pattern {@code "/"}), that method's legacy per-servlet-mapping
   * logic returns an empty string rather than the full path, since the servlet spec treats a {@code
   * "/"} mapping as consuming the entire path into {@code servletPath} with a {@code null
   * pathInfo}. Instead, this only strips the context path (via {@code getPathWithinApplication},
   * which has no such quirk) and, if configured, the given (already-normalized) servlet path
   * explicitly and predictably as a plain string prefix.
   */
  static boolean isWebhookPath(HttpServletRequest request, String normalizedServletPath) {
    String path = URL_PATH_HELPER.getPathWithinApplication(request);
    if (!normalizedServletPath.isEmpty() && path.startsWith(normalizedServletPath)) {
      path = path.substring(normalizedServletPath.length());
    }
    return path.equals(WEBHOOK_PATH) || path.startsWith(WEBHOOK_PATH_PREFIX);
  }
}
