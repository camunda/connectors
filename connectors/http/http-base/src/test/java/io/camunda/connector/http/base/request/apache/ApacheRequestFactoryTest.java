/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.http.base.request.apache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpMethod;
import java.util.Map;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHeaders;
import org.junit.jupiter.api.Test;

public class ApacheRequestFactoryTest {

  @Test
  public void shouldSetJsonContentType_WhenNotProvidedAndSupportsBody() throws Exception {
    // given request without headers
    HttpCommonRequest request = new HttpCommonRequest();
    request.setMethod(HttpMethod.POST);

    // when
    ClassicHttpRequest httpRequest = ApacheRequestFactory.get().createHttpRequest(request);

    // then
    Header headers = httpRequest.getHeader(HttpHeaders.CONTENT_TYPE);
    assertNotNull(headers);
    assertThat(headers.getValue()).isEqualTo(ContentType.APPLICATION_JSON.getMimeType());
  }

  @Test
  public void shouldSetJsonContentType_WhenNotProvidedAndSupportsBodyAndSomeHeadersExist()
      throws Exception {
    // given request without headers
    HttpCommonRequest request = new HttpCommonRequest();
    request.setHeaders(Map.of("Authorization", "Bearer token"));
    request.setMethod(HttpMethod.POST);

    // when
    ClassicHttpRequest httpRequest = ApacheRequestFactory.get().createHttpRequest(request);

    // then
    assertThat(httpRequest.getHeaders().length).isEqualTo(2);
    Header headers = httpRequest.getHeader(HttpHeaders.CONTENT_TYPE);
    assertNotNull(headers);
    assertThat(headers.getValue()).isEqualTo(ContentType.APPLICATION_JSON.getMimeType());
  }

  @Test
  public void shouldNotSetJsonContentType_WhenNotProvidedAndDoesNotSupportBody() throws Exception {
    // given request without headers
    HttpCommonRequest request = new HttpCommonRequest();
    request.setMethod(HttpMethod.GET);

    // when
    ClassicHttpRequest httpRequest = ApacheRequestFactory.get().createHttpRequest(request);

    // then
    Header headers = httpRequest.getHeader(HttpHeaders.CONTENT_TYPE);
    assertNull(headers);
  }

  @Test
  public void shouldNotSetJsonContentType_WhenProvided() throws Exception {
    // given request without headers
    HttpCommonRequest request = new HttpCommonRequest();
    request.setHeaders(Map.of(HttpHeaders.CONTENT_TYPE, "text/plain"));
    request.setMethod(HttpMethod.POST);

    // when
    ClassicHttpRequest httpRequest = ApacheRequestFactory.get().createHttpRequest(request);

    // then
    Header headers = httpRequest.getHeader(HttpHeaders.CONTENT_TYPE);
    assertNotNull(headers);
    assertThat(headers.getValue()).isEqualTo(ContentType.TEXT_PLAIN.getMimeType());
  }
}
