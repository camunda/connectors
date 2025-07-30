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
package io.camunda.connector.http.client.client.apache;

import io.camunda.connector.http.client.client.RequestFactory;
import io.camunda.connector.http.client.client.apache.builder.ApacheRequestBuilder;
import io.camunda.connector.http.client.model.HttpClientRequest;
import org.apache.hc.core5.http.ClassicHttpRequest;

/**
 * Maps a {@link HttpClientRequest}(element template model) to an Apache {@link ClassicHttpRequest}.
 */
public class ApacheRequestFactory implements RequestFactory<ClassicHttpRequest> {
  private static final ApacheRequestFactory INSTANCE = new ApacheRequestFactory();

  private ApacheRequestFactory() {}

  public static ApacheRequestFactory get() {
    return INSTANCE;
  }

  @Override
  public ClassicHttpRequest createHttpRequest(HttpClientRequest request) {
    return ApacheRequestBuilder.create().build(request);
  }
}
