/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DataLookupServiceTest {
  @Test
  public void shouldConvertStringToList() {
    String stringToConvertToList = "test@test.com,@test1, test2 ,  @test3 ";
    List<String> result = DataLookupService.convertStringToList(stringToConvertToList);
    assertEquals(4, result.size());
    assertEquals("@test3", result.get(3));
  }

  @ParameterizedTest
  @CsvSource({
    "test@test.com,true",
    "t.e.s.t@test.com,true",
    "test@,false",
    "@test,false",
    "test.com,false",
    "test,false"
  })
  public void shouldReturnTrueForEmailCheck(String email, boolean result) {
    assertEquals(DataLookupService.isEmail(email), result);
  }
}
