/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;

public class Base64HelperTest {

  @Test
  public void shouldReturnBasicHeaderEncoded() {
    // given
    String username = "username";
    String password = "password";
    String encoded = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());

    // when
    String result = Base64Helper.buildBasicAuthenticationHeader(username, password);

    // then
    assertThat(result).isEqualTo("Basic " + encoded);
  }
}
