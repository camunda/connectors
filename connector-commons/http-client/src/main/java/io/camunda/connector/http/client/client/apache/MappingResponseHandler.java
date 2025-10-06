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

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.client.exception.ConnectorExceptionMapper;
import io.camunda.connector.http.client.mapper.MappedHttpResponse;
import io.camunda.connector.http.client.mapper.ResponseMapper;
import io.camunda.connector.http.client.mapper.StreamingHttpResponse;
import io.camunda.connector.http.client.utils.HttpStatusHelper;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MappingResponseHandler<T> implements HttpClientResponseHandler<MappedHttpResponse<T>> {

  private final ResponseMapper<T> responseMapper;

  public MappingResponseHandler(ResponseMapper<T> responseMapper) {
    this.responseMapper = responseMapper;
  }

  private static final Logger LOGGER = LoggerFactory.getLogger(MappingResponseHandler.class);

  @Override
  public MappedHttpResponse<T> handleResponse(ClassicHttpResponse response) {
    int code = response.getCode();
    String reason = response.getReasonPhrase();
    Map<String, List<String>> headers = MappingResponseHandler.formatHeaders(response.getHeaders());

    if (response.getEntity() != null) {

      try (InputStream content = response.getEntity().getContent()) {
        StreamingHttpResponse rawResponse =
            new StreamingHttpResponse(code, reason, headers, content);

        if (HttpStatusHelper.isError(code)) {
          throw ConnectorExceptionMapper.from(rawResponse);
        }

        T mappedResponse = responseMapper.apply(rawResponse);
        return new MappedHttpResponse<>(code, reason, headers, mappedResponse);
      } catch (final ConnectorException e) {
        throw e; // rethrow in case of error status
      } catch (final Exception e) {
        LOGGER.error("Failed to process response: {}", response, e);
        return new MappedHttpResponse<>(HttpStatus.SC_SERVER_ERROR, e.getMessage(), Map.of(), null);
      }
    }
    if (HttpStatusHelper.isError(code)) {
      StreamingHttpResponse rawResponse = new StreamingHttpResponse(code, reason, headers, null);
      throw ConnectorExceptionMapper.from(rawResponse);
    }
    return new MappedHttpResponse<>(code, reason, headers, null);
  }

  private static Map<String, List<String>> formatHeaders(Header[] headersArray) {
    return Arrays.stream(headersArray)
        .collect(
            Collectors.groupingBy(
                Header::getName, Collectors.mapping(Header::getValue, Collectors.toList())));
  }
}
