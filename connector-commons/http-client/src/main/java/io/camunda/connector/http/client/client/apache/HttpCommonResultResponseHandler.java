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

import static io.camunda.connector.http.client.utils.JsonHelper.isJsonStringValid;

import io.camunda.connector.http.client.ExecutionEnvironment;
import io.camunda.connector.http.client.HttpClientObjectMapperSupplier;
import io.camunda.connector.http.client.document.FileResponseHandler;
import io.camunda.connector.http.client.model.HttpClientResult;
import io.camunda.connector.http.client.model.CustomHttpBody;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
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

  @Override
  public HttpClientResult handleResponse(ClassicHttpResponse response) {
    int code = response.getCode();
    String reason = response.getReasonPhrase();
    Map<String, Object> headers =
        HttpCommonResultResponseHandler.formatHeaders(response.getHeaders());

    if (response.getEntity() != null) {
      try {
        return new HttpClientResult(
            code,
            headers,
            new CustomHttpBody(
                // content stream to be closed when the response is closed
                response.getEntity().getContent(),
                response.getEntity().getContentLength(),
                response.getEntity().getContentType()),
            reason);
      } catch (Exception e) {
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

  /**
   * Extracts the body from the response content. Tries to parse the body as JSON, if it fails,
   * returns the body as a string.
   *
   * @param content the response content
   */
  private Object extractBody(byte[] content) throws IOException {

    String bodyString = null;
    if (content != null) {
      bodyString = new String(content, StandardCharsets.UTF_8);
    }

    if (StringUtils.isNotBlank(bodyString)) {
      return isJsonStringValid(bodyString)
          ? HttpClientObjectMapperSupplier.getCopy().readValue(bodyString, Object.class)
          : bodyString;
    }
    return null;
  }
}
