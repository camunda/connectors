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
package io.camunda.connector.http.base.utils;

import com.google.api.client.http.HttpRequest;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class Timeout {

  public static void copyTimeoutFrom(HttpCommonRequest request, HttpRequest httpRequest) {
    copyTimeoutFrom(
        httpRequest,
        request.getConnectionTimeoutInSeconds(),
        request.getReadTimeoutInSeconds(),
        request.getWriteTimeoutInSeconds());
  }

  public static void copyTimeoutFrom(
      final HttpRequest httpRequest,
      final Integer connectionTimeoutInSeconds,
      final Integer readTimeoutInSeconds,
      final Integer writeTimeoutInSeconds) {
    Optional<Integer> connectionTimeoutInMillis = toMillis(connectionTimeoutInSeconds);
    connectionTimeoutInMillis.ifPresent(httpRequest::setConnectTimeout);
    toMillis(readTimeoutInSeconds).ifPresent(httpRequest::setReadTimeout);
    toMillis(writeTimeoutInSeconds).ifPresent(httpRequest::setWriteTimeout);
    // backward compatibility
    if (writeTimeoutInSeconds == null && readTimeoutInSeconds == null) {
      connectionTimeoutInMillis.ifPresent(
          timeout -> {
            httpRequest.setReadTimeout(timeout);
            httpRequest.setWriteTimeout(timeout);
          });
    }
  }

  private static Optional<Integer> toMillis(Integer seconds) {
    return Optional.ofNullable(seconds).map(s -> Math.toIntExact(TimeUnit.SECONDS.toMillis(s)));
  }
}
