/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonPrimitive;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MemberTest {

  @ParameterizedTest
  @ValueSource(strings = {"24e52453sdgsdtr", "name@mail.com", "!!!)))__"})
  public void getAsGraphJsonPrimitive_shouldReturnCorrectValue(String input) {
    // Given members and input value
    Member member = new Member();
    member.setUserId(input);
    member.setUserPrincipalName(null);

    Member member_2 = new Member();
    member_2.setUserId(null);
    member_2.setUserPrincipalName(input);
    // When
    JsonPrimitive fromUser = member.getAsGraphJsonPrimitive();
    JsonPrimitive fromPrincipalName = member_2.getAsGraphJsonPrimitive();
    JsonPrimitive fromValue = Member.toGraphJsonPrimitive(input);
    // Then
    assertThat(fromUser).isEqualTo(fromPrincipalName).isEqualTo(fromValue);
  }
}
