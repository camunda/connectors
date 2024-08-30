/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.http.base.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class HttpCommonRequestTest {

  @Test
  public void shouldCopyReadTimeoutFromConnection_whenNoReadTimeoutIsSet() {
    // given
    HttpCommonRequest request = new HttpCommonRequest();
    request.setConnectionTimeoutInSeconds(10);

    // when
    int readTimeout = request.getReadTimeoutInSeconds();

    // then
    assertThat(readTimeout).isEqualTo(10);
  }

  @Test
  public void shouldNotCopyReadTimeoutFromConnection_whenReadTimeoutIsSet() {
    // given
    HttpCommonRequest request = new HttpCommonRequest();
    request.setConnectionTimeoutInSeconds(10);
    request.setReadTimeoutInSeconds(20);

    // when
    int readTimeout = request.getReadTimeoutInSeconds();

    // then
    assertThat(readTimeout).isEqualTo(20);
  }

  @Test
  public void shouldUseDefaultReadTimeout_whenNoReadTimeoutIsSetAndNoConnectionTimeoutIsSet() {
    // given
    HttpCommonRequest request = new HttpCommonRequest();

    // when
    int readTimeout = request.getReadTimeoutInSeconds();

    // then
    assertThat(readTimeout).isEqualTo(20);
  }
}
