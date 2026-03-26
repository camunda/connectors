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
package io.camunda.connector.http.client.proxy;

import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

public interface ProxyConfiguration {
  String SCHEME_HTTP = "http";
  String SCHEME_HTTPS = "https";

  /** A no-op proxy configuration that never returns proxy details. */
  ProxyConfiguration NONE = protocol -> Optional.empty();

  Optional<ProxyDetails> getProxyDetails(String protocol);

  record ProxyDetails(String scheme, String host, int port, String user, String password) {
    public boolean hasCredentials() {
      return StringUtils.isNotBlank(user) && StringUtils.isNotEmpty(password);
    }
  }
}
