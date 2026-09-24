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
import jakarta.servlet.http.MappingMatch;
import org.springframework.web.util.UrlPathHelper;

final class WebhookFilterPaths {

  private static final String WEBHOOK_PATH = "/inbound";
  private static final String WEBHOOK_PATH_PREFIX = WEBHOOK_PATH + "/";
  private static final UrlPathHelper URL_PATH_HELPER = new UrlPathHelper();

  private WebhookFilterPaths() {}

  static String normalizeServletPath(String servletPath) {
    if (servletPath == null) {
      return "";
    }
    String path = servletPath;
    int wildcardIndex = path.indexOf('*');
    if (wildcardIndex != -1) {
      path = path.substring(0, wildcardIndex);
    }
    if (path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }
    return path;
  }

  static boolean isMultipartFormData(String contentType) {
    if (contentType == null) {
      return false;
    }
    int parameterSeparator = contentType.indexOf(';');
    String mediaType =
        parameterSeparator == -1 ? contentType : contentType.substring(0, parameterSeparator);
    return mediaType.trim().equalsIgnoreCase("multipart/form-data");
  }

  static boolean isWebhookPath(HttpServletRequest request, String normalizedConfiguredServletPath) {
    String path = URL_PATH_HELPER.getPathWithinApplication(request);
    String prefix = servletPathPrefix(request, normalizedConfiguredServletPath);
    if (!prefix.isEmpty() && (path.equals(prefix) || path.startsWith(prefix + "/"))) {
      path = path.substring(prefix.length());
    }
    return path.equals(WEBHOOK_PATH) || path.startsWith(WEBHOOK_PATH_PREFIX);
  }

  private static String servletPathPrefix(
      HttpServletRequest request, String normalizedConfiguredServletPath) {
    var mapping = request.getHttpServletMapping();
    MappingMatch match = mapping == null ? null : mapping.getMappingMatch();
    if (match == MappingMatch.PATH) {
      return normalizeServletPath(request.getServletPath());
    }
    if (match == MappingMatch.DEFAULT || match == MappingMatch.CONTEXT_ROOT) {
      return "";
    }
    return normalizedConfiguredServletPath;
  }
}
