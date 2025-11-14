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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.client.HttpClientObjectMapperSupplier;
import io.camunda.connector.http.client.mapper.HttpResponse;
import io.camunda.connector.http.client.mapper.ResponseMappers;
import java.util.List;
import java.util.function.Supplier;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.Test;

public class CustomResponseHandlerTest {

  @Test
  public void shouldHandleJsonResponse() {
    // given
    CustomResponseHandler<JsonNode> handler =
        new CustomResponseHandler<>(
            ResponseMappers.asJsonNode(HttpClientObjectMapperSupplier::getCopy));

    ClassicHttpResponse response = new BasicClassicHttpResponse(200);
    Header[] headers = new Header[] {new BasicHeader("Content-Type", "application/json")};
    response.setHeaders(headers);
    StringEntity entity = new StringEntity("{\"key\":\"value\"}");
    response.setEntity(entity);

    // when
    HttpResponse<JsonNode> result = handler.handleResponse(response);

    // then
    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(200);
    assertThat(result.entity().get("key").asText()).isEqualTo("value");
    assertThat(result.headers()).hasSize(1);
    assertThat(result.headers()).containsEntry("Content-Type", List.of("application/json"));
  }

  @Test
  public void shouldHandleTextResponse() {
    // given
    CustomResponseHandler<String> handler = new CustomResponseHandler<>(ResponseMappers.asString());
    ClassicHttpResponse response = new BasicClassicHttpResponse(200);
    Header[] headers = new Header[] {new BasicHeader("Content-Type", "text/plain")};
    response.setHeaders(headers);
    response.setEntity(new StringEntity("text"));

    // when
    HttpResponse<String> result = handler.handleResponse(response);

    // then
    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(200);
    assertThat(result.entity()).isEqualTo("text");
    assertThat(result.headers()).hasSize(1);
    assertThat(result.headers()).containsEntry("Content-Type", List.of("text/plain"));
  }

  @Test
  public void shouldHandleErrorResponse() {
    // given
    CustomResponseHandler<Void> handler = new CustomResponseHandler<>((response) -> null);
    ClassicHttpResponse response = new BasicClassicHttpResponse(500);
    StringEntity entity = new StringEntity("Internal Server Error: something went wrong");
    response.setEntity(entity);

    // when
    Supplier<HttpResponse<Void>> resultSupplier = () -> handler.handleResponse(response);

    ConnectorException connectorException =
        assertThrows(ConnectorException.class, resultSupplier::get);
    assertThat(connectorException.getMessage()).isEqualTo("Internal Server Error");
  }
}
