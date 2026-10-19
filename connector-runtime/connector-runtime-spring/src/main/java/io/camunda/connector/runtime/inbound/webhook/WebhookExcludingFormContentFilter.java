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

public class WebhookExcludingFormContentFilter extends FormContentFilter implements Ordered {

  private static final int ORDER = -9900;

  private final String servletPath;

  public WebhookExcludingFormContentFilter(String servletPath) {
    this.servletPath = WebhookFilterPaths.normalizeServletPath(servletPath);
  }

  @Override
  public int getOrder() {
    return ORDER;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
    return WebhookFilterPaths.isWebhookPath(request, servletPath) || super.shouldNotFilter(request);
  }
}
