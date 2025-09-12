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

import io.camunda.connector.api.document.Document;
import io.camunda.connector.http.client.ExecutionEnvironment;
import io.camunda.connector.http.client.HttpClientObjectMapperSupplier;
import io.camunda.connector.http.client.client.HttpStatusHelper;
import io.camunda.connector.http.client.client.apache.CustomHttpBody.BytesBody;
import io.camunda.connector.http.client.client.apache.CustomHttpBody.StringBody;
import io.camunda.connector.http.client.document.DocumentCreationException;
import io.camunda.connector.http.client.document.FileResponseHandler;
import io.camunda.connector.http.client.model.ErrorResponse;
import io.camunda.connector.http.client.model.HttpClientResult;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
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

  private final FileResponseHandler fileResponseHandler;

  private final ExecutionEnvironment executionEnvironment;

  private final boolean isStoreResponseSelected;

  public HttpCommonResultResponseHandler(
      @Nullable ExecutionEnvironment executionEnvironment, boolean isStoreResponseSelected) {
    this.executionEnvironment = executionEnvironment;
    this.isStoreResponseSelected = isStoreResponseSelected;
    this.fileResponseHandler =
        new FileResponseHandler(executionEnvironment, isStoreResponseSelected);
  }

  @Override
  public HttpClientResult handleResponse(ClassicHttpResponse response) {
    int code = response.getCode();
    String reason = response.getReasonPhrase();
    Map<String, Object> headers =
        HttpCommonResultResponseHandler.formatHeaders(response.getHeaders());

    if (response.getEntity() != null) {
      try (InputStream content = response.getEntity().getContent()) {
        if (executionEnvironment instanceof ExecutionEnvironment.SaaSCluster) {
          return getResultForCloudFunction(code, content, headers, reason);
        }
        var bytes = content.readAllBytes();
        var documentReference = fileResponseHandler.handle(headers, bytes);
        return new HttpClientResult(
            code,
            headers,
            documentReference == null ? extractBody(bytes) : null,
            reason,
            documentReference);
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

  /**
   * Will parse the response as a Cloud Function response. If the response is an error, it will be
   * unwrapped as an ErrorResponse. Otherwise, it will be unwrapped as a HttpCommonResult.
   */
  private HttpClientResult getResultForCloudFunction(
      int code, InputStream content, Map<String, Object> headers, String reason)
      throws IOException, DocumentCreationException {
    if (HttpStatusHelper.isError(code)) {
      // unwrap as ErrorResponse
      var errorResponse =
          HttpClientObjectMapperSupplier.getCopy().readValue(content, ErrorResponse.class);
      return new HttpClientResult(code, headers, errorResponse, reason);
    }
    // Unwrap the response as a HttpCommonResult directly
    var result =
        HttpClientObjectMapperSupplier.getCopy().readValue(content, HttpClientResult.class);
    Document document = fileResponseHandler.handleCloudFunctionResult(result);
    return new HttpClientResult(
        result.status(),
        result.headers(),
        document == null ? result.body() : null,
        result.reason(),
        document);
  }

  /**
   * Extracts the body from the response content. Tries to parse the body as JSON, if it fails,
   * returns the body as a string.
   *
   * @param content the response content
   */
  private CustomHttpBody extractBody(byte[] content) {
    if (executionEnvironment instanceof ExecutionEnvironment.SaaSCloudFunction
        && isStoreResponseSelected) {
      return new StringBody(Base64.getEncoder().encodeToString(content));
    }

    return new BytesBody(content);
  }
}
