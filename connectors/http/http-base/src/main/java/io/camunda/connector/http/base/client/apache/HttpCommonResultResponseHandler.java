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
package io.camunda.connector.http.base.client.apache;

import static io.camunda.connector.http.base.utils.JsonHelper.isJsonStringValid;

import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.http.base.ExecutionEnvironment;
import io.camunda.connector.http.base.client.HttpStatusHelper;
import io.camunda.connector.http.base.document.FileResponseHandler;
import io.camunda.connector.http.base.model.ErrorResponse;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.document.reference.DocumentReference;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
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

  private final FileResponseHandler fileResponseHandler;

  private final ExecutionEnvironment executionEnvironment;

  public HttpCommonResultResponseHandler(@Nullable ExecutionEnvironment executionEnvironment) {
    this.executionEnvironment = executionEnvironment;
    this.fileResponseHandler = new FileResponseHandler(executionEnvironment);
  }

  @Override
  public HttpCommonResult handleResponse(ClassicHttpResponse response) {
    int code = response.getCode();
    String reason = response.getReasonPhrase();
    Map<String, String> headers =
        Arrays.stream(response.getHeaders())
            .collect(
                // Collect the headers into a map ignoring duplicates (Set Cookies for instance)
                Collectors.toMap(Header::getName, Header::getValue, (first, second) -> first));
    if (response.getEntity() != null) {
      try (InputStream content = response.getEntity().getContent()) {
        if (executionEnvironment instanceof ExecutionEnvironment.SaaSCallerSideEnvironment) {
          return getResultForCloudFunction(code, content, headers, reason);
        }
        var bytes = content.readAllBytes();
        var documentReference = handleFileResponse(headers, bytes);
        return new HttpCommonResult(
            code,
            headers,
            documentReference == null ? extractBody(bytes) : null,
            reason,
            documentReference);
      } catch (final Exception e) {
        LOGGER.error("Failed to parse external response: {}", response, e);
      }
    }
    return new HttpCommonResult(code, headers, null, reason);
  }

  private DocumentReference handleFileResponse(Map<String, String> headers, byte[] content) {
    var documentReference = fileResponseHandler.handle(headers, content);
    LOGGER.debug("Stored response as document. Document reference: {}", documentReference);
    return documentReference;
  }

  /**
   * Will parse the response as a Cloud Function response. If the response is an error, it will be
   * unwrapped as an ErrorResponse. Otherwise, it will be unwrapped as a HttpCommonResult.
   */
  private HttpCommonResult getResultForCloudFunction(
      int code, InputStream content, Map<String, String> headers, String reason)
      throws IOException {
    if (HttpStatusHelper.isError(code)) {
      // unwrap as ErrorResponse
      var errorResponse =
          ConnectorsObjectMapperSupplier.getCopy().readValue(content, ErrorResponse.class);
      return new HttpCommonResult(code, headers, errorResponse, reason);
    }
    // Unwrap the response as a HttpCommonResult directly
    var result =
        ConnectorsObjectMapperSupplier.getCopy().readValue(content, HttpCommonResult.class);
    DocumentReference documentReference = fileResponseHandler.handleCloudFunctionResult(result);
    return new HttpCommonResult(
        result.status(),
        result.headers(),
        documentReference == null ? result.body() : null,
        result.reason(),
        documentReference);
  }

  /**
   * Extracts the body from the response content. Tries to parse the body as JSON, if it fails,
   * returns the body as a string.
   *
   * @param content the response content
   */
  private Object extractBody(byte[] content) throws IOException {
    if (executionEnvironment != null && executionEnvironment.storeResponseSelected()) {
      return Base64.getEncoder().encodeToString(content);
    }

    String bodyString = null;
    if (content != null) {
      bodyString = new String(content, StandardCharsets.UTF_8);
    }

    if (StringUtils.isNotBlank(bodyString)) {
      return isJsonStringValid(bodyString)
          ? ConnectorsObjectMapperSupplier.getCopy().readValue(bodyString, Object.class)
          : bodyString;
    }
    return null;
  }
}
