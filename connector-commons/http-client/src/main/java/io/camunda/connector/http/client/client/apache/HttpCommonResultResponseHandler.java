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

import io.camunda.connector.http.client.model.HttpClientResult;
import io.camunda.connector.http.client.model.ResponseBody;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpCommonResultResponseHandler
    implements HttpClientResponseHandler<HttpClientResult> {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(HttpCommonResultResponseHandler.class);

  public HttpCommonResultResponseHandler() {}

  @Override
  public HttpClientResult handleResponse(ClassicHttpResponse response) {
    int code = response.getCode();
    String reason = response.getReasonPhrase();
    Map<String, Object> headers =
        HttpCommonResultResponseHandler.formatHeaders(response.getHeaders());

    if (response.getEntity() != null) {
      try {
        // stream must be closed by the caller
        InputStream content = response.getEntity().getContent();
        ResponseBody body = new ResponseBody(content);
        return new HttpClientResult(code, headers, body, reason);
      } catch (final Exception e) {
        LOGGER.error("Failed to process response: {}", response, e);
        return new HttpClientResult(HttpStatus.SC_SERVER_ERROR, Map.of(), null, e.getMessage());
      }
    }
    return new HttpClientResult(code, headers, null, reason);
  }

  private static Map<String, Object> formatHeaders(Header[] headersArray) {
    return Arrays.stream(headersArray)
        .collect(
            Collectors.toMap(
                Header::getName,
                header -> {
                  if (header.getName().equalsIgnoreCase("Set-Cookie")) {
                    return new ArrayList<String>(List.of(header.getValue()));
                  } else {
                    return header.getValue();
                  }
                },
                (existingValue, newValue) -> {
                  if (existingValue instanceof List && newValue instanceof List) {
                    ((List<String>) existingValue).add(((List<String>) newValue).getFirst());
                  }
                  return existingValue;
                }));
  }
}
