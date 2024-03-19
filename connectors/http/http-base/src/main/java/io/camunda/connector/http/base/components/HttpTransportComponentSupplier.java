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
package io.camunda.connector.http.base.components;

import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.apache.v2.ApacheHttpTransport;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.gson.GsonFactory;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.client.HttpClientBuilder;

public class HttpTransportComponentSupplier {

  private HttpTransportComponentSupplier() {}

  private static final HttpTransport HTTP_TRANSPORT =
      new ApacheHttpTransport(
          HttpClientBuilder.create()
              .setMaxConnTotal(Integer.MAX_VALUE)
              .setMaxConnPerRoute(Integer.MAX_VALUE)
              .setDefaultSocketConfig(SocketConfig.custom().setSoKeepAlive(true).build())
              .build());
  private static final HttpRequestFactory REQUEST_FACTORY =
      HTTP_TRANSPORT.createRequestFactory(
          request -> request.setParser(new JsonObjectParser(new GsonFactory())));

  public static HttpRequestFactory httpRequestFactoryInstance() {
    return REQUEST_FACTORY;
  }
}
