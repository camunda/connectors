/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.http.base.components.apache;

import static io.camunda.connector.http.base.utils.JsonHelper.isJsonValid;

import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.http.base.model.HttpCommonResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpCommonResultResponseHandler
    implements HttpClientResponseHandler<HttpCommonResult> {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(HttpCommonResultResponseHandler.class);

  boolean remoteExecutionEnabled;

  public HttpCommonResultResponseHandler(boolean remoteExecutionEnabled) {
    this.remoteExecutionEnabled = remoteExecutionEnabled;
  }

  @Override
  public HttpCommonResult handleResponse(ClassicHttpResponse response) {
    HttpCommonResult result = new HttpCommonResult();

    if (response.getEntity() != null) {
      try (InputStream content = response.getEntity().getContent()) {
        if (remoteExecutionEnabled) {
          // Unwrap the response as a HttpCommonResult directly
          return ConnectorsObjectMapperSupplier.DEFAULT_MAPPER.readValue(
              content, HttpCommonResult.class);
        }
        result.setStatus(response.getCode());
        result.setHeaders(
            Arrays.stream(response.getHeaders())
                .collect(Collectors.toMap(Header::getName, Header::getValue)));
        result.setBody(extractBody(content));
      } catch (final Exception e) {
        LOGGER.error("Failed to parse external response: {}", response, e);
      }
    }
    return result;
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

    if (bodyString != null) {
      return isJsonValid(bodyString)
          ? ConnectorsObjectMapperSupplier.DEFAULT_MAPPER.readValue(bodyString, Object.class)
          : bodyString;
    }
    return null;
  }
}
