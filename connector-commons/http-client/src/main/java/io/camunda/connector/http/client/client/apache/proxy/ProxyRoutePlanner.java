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
package io.camunda.connector.http.client.client.apache.proxy;

import io.camunda.connector.http.client.proxy.NonProxyHostsMatcher;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyRoutePlanner extends DefaultProxyRoutePlanner {
  private static final Logger LOG = LoggerFactory.getLogger(ProxyRoutePlanner.class.getName());

  public ProxyRoutePlanner(HttpHost proxy) {
    super(proxy);
  }

  @Override
  protected HttpHost determineProxy(HttpHost target, HttpContext context) throws HttpException {
    if (NonProxyHostsMatcher.isNonProxyHost(target.getHostName())) {
      LOG.debug(
          "Not using proxy for target host [{}] as it matched either system properties (http.nonProxyHosts) or environment variables ({})",
          target.getHostName(),
          ProxyConfiguration.CONNECTOR_HTTP_NON_PROXY_HOSTS_ENV_VAR);
      return null;
    }
    var proxy = super.determineProxy(target, context);
    LOG.debug("Using proxy for target host [{}] => [{}]", target.getHostName(), proxy);
    return proxy;
  }
}
