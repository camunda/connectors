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
package io.camunda.connector.http.base.components.apache;

import static io.camunda.connector.http.base.utils.JsonHelper.isJsonStringValid;

import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.http.base.model.ErrorResponse;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.base.utils.HttpStatusHelper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpCommonResultResponseHandler
    implements HttpClientResponseHandler<HttpCommonResult> {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(HttpCommonResultResponseHandler.class);

  boolean cloudFunctionEnabled;

  public HttpCommonResultResponseHandler(boolean cloudFunctionEnabled) {
    this.cloudFunctionEnabled = cloudFunctionEnabled;
  }

  @Override
  public HttpCommonResult handleResponse(ClassicHttpResponse response) {
    int code = response.getCode();
    String reason = response.getReasonPhrase();
    Map<String, Object> headers =
        Arrays.stream(response.getHeaders())
            .collect(Collectors.toMap(Header::getName, Header::getValue));
    if (response.getEntity() != null) {
      try (InputStream content = response.getEntity().getContent()) {
        if (cloudFunctionEnabled) {
          return getResultForCloudFunction(code, content, headers, reason);
        }
        return new HttpCommonResult(code, headers, extractBody(content), reason);
      } catch (final Exception e) {
        LOGGER.error("Failed to parse external response: {}", response, e);
      }
    }
    return new HttpCommonResult(code, headers, null, reason);
  }

  /**
   * Will parse the response as a Cloud Function response. If the response is an error, it will be
   * unwrapped as an ErrorResponse. Otherwise, it will be unwrapped as a HttpCommonResult.
   */
  private HttpCommonResult getResultForCloudFunction(
      int code, InputStream content, Map<String, Object> headers, String reason)
      throws IOException {
    if (HttpStatusHelper.isError(code)) {
      // unwrap as ErrorResponse
      var errorResponse =
          ConnectorsObjectMapperSupplier.DEFAULT_MAPPER.readValue(content, ErrorResponse.class);
      return new HttpCommonResult(code, headers, errorResponse, reason);
    }
    // Unwrap the response as a HttpCommonResult directly
    return ConnectorsObjectMapperSupplier.DEFAULT_MAPPER.readValue(content, HttpCommonResult.class);
  }

  /**
   * Extracts the body from the response content. Tries to parse the body as JSON, if it fails,
   * returns the body as a string.
   */
  private Object extractBody(InputStream content) throws IOException {
    String bodyString = null;
    if (content != null) {
      bodyString = new String(content.readAllBytes(), StandardCharsets.UTF_8);
    }

    if (StringUtils.isNotBlank(bodyString)) {
      return isJsonStringValid(bodyString)
          ? ConnectorsObjectMapperSupplier.DEFAULT_MAPPER.readValue(bodyString, Object.class)
          : bodyString;
    }
    return null;
  }
}
