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
package io.camunda.connector.http.base.client.apache.builder.parts;

import io.camunda.connector.http.base.model.HttpCommonRequest;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

public class ApacheRequestUriBuilder implements ApacheRequestPartBuilder {

  @Override
  public void build(ClassicRequestBuilder builder, HttpCommonRequest request) {
    try {
      var url = new URL(request.getUrl());
      builder.setUri(
          new URI(
              url.getProtocol(),
              url.getUserInfo(),
              url.getHost(),
              url.getPort(),
              url.getPath(),
              url.getQuery(),
              null));
    } catch (MalformedURLException | URISyntaxException e) {
      builder.setUri(request.getUrl());
    }
  }
}
