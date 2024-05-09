/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class ConnectionParameterHelperTest {
  @Test
  void shouldCreateQueryParameters_whenNoExistingQueryParameters() {
    String urlString = "jdbc:mysql//localhost:3306";
    String paramName = "paramName";
    String paramValue = "paramValue";
    String result =
        ConnectionParameterHelper.addQueryParameterToURL(urlString, paramName, paramValue);
    assertThat(result).isEqualTo(urlString + "?paramName=paramValue");
  }

  @Test
  void shouldNotCreateQueryParameters_whenExistingQueryParameters() {
    String urlString = "jdbc:mysql//localhost:3306?existingParam=existingValue";
    String paramName = "paramName";
    String paramValue = "paramValue";
    String result =
        ConnectionParameterHelper.addQueryParameterToURL(urlString, paramName, paramValue);
    assertThat(result).isEqualTo(urlString + "&paramName=paramValue");
  }

  @Test
  void shouldCreateQueryParameters_whenNoParamValue() {
    String urlString = "jdbc:mysql//localhost:3306";
    String paramName = "paramName";
    String result = ConnectionParameterHelper.addQueryParameterToURL(urlString, paramName);
    assertThat(result).isEqualTo(urlString + "?paramName");
  }

  @Test
  void shouldCreateQueryParameters_whenNoParamValueAndExistingQueryParameters() {
    String urlString = "jdbc:mysql//localhost:3306?existingParam=existingValue";
    String paramName = "paramName";
    String result = ConnectionParameterHelper.addQueryParameterToURL(urlString, paramName);
    assertThat(result).isEqualTo(urlString + "&paramName");
  }

  @Test
  void shouldCreateQueryParametersAfterPath_whenQueryPathExistsAndNoParamValue() {
    String urlString = "jdbc:mysql//localhost:3306/database";
    String paramName = "paramName";
    String result = ConnectionParameterHelper.addQueryParameterToURL(urlString, paramName);
    assertThat(result).isEqualTo(urlString + "?paramName");
  }

  @Test
  void shouldCreateQueryParametersAfterPath_whenQueryPathExistsAndQueryParametersExist() {
    String urlString = "jdbc:mysql//localhost:3306/database?existingParam=existingValue";
    String paramName = "paramName";
    String result = ConnectionParameterHelper.addQueryParameterToURL(urlString, paramName);
    assertThat(result).isEqualTo(urlString + "&paramName");
  }

  @Test
  void shouldCreateQueryParametersAfterPath_whenEmptyPath() {
    String urlString = "jdbc:mysql//localhost:3306/";
    String paramName = "paramName";
    String result = ConnectionParameterHelper.addQueryParameterToURL(urlString, paramName);
    assertThat(result).isEqualTo(urlString + "?paramName");
  }

  @Test
  void
      shouldCreateQueryParametersAfterPath_whenQueryPathExistsAndQueryParametersExistAndPasswordHasWeirdChars() {
    String urlString = "jdbc:mysql//localhost:3306/database?user=test&password=ab#!xij:()_s23";
    String paramName = "paramName";
    String result = ConnectionParameterHelper.addQueryParameterToURL(urlString, paramName);
    assertThat(result).isEqualTo(urlString + "&paramName");
  }

  @Test
  void shouldCreateQueryParameters_whenMultipleExistingQueryParameters() {
    String urlString =
        "jdbc:mysql//localhost:3306?existingParam1=existingValue1&existingParam2=existingValue2";
    String paramName = "paramName";
    String paramValue = "paramValue";
    String result =
        ConnectionParameterHelper.addQueryParameterToURL(urlString, paramName, paramValue);
    assertThat(result).isEqualTo(urlString + "&paramName=paramValue");
  }

  @Test
  void shouldEncodeSpecialCharactersInParamValue() {
    String urlString = "jdbc:mysql//localhost:3306";
    String paramName = "paramName";
    String paramValue = "special&=value%";
    String result =
        ConnectionParameterHelper.addQueryParameterToURL(urlString, paramName, paramValue);
    assertThat(result).isEqualTo(urlString + "?paramName=special%26%3Dvalue%25");
  }

  @Test
  void shouldHandleEmptyParamValue() {
    String urlString = "jdbc:mysql//localhost:3306";
    String paramName = "paramName";
    String paramValue = "";
    String result =
        ConnectionParameterHelper.addQueryParameterToURL(urlString, paramName, paramValue);
    assertThat(result).isEqualTo(urlString + "?paramName=");
  }

  @Test
  void shouldHandleNullParamValue() {
    String urlString = "jdbc:mysql//localhost:3306";
    String paramName = "paramName";
    String result = ConnectionParameterHelper.addQueryParameterToURL(urlString, paramName, null);
    assertThat(result).isEqualTo(urlString + "?paramName");
  }

  @Test
  void shouldHandleParameterValueWithAmpersand() {
    String urlString = "jdbc:mysql//localhost:3306";
    String paramName = "paramName";
    String paramValue = "value1&value2";
    String result =
        ConnectionParameterHelper.addQueryParameterToURL(urlString, paramName, paramValue);
    assertThat(result).isEqualTo(urlString + "?paramName=value1%26value2");
  }

  @Test
  void shouldHandleParameterValueWithEqualsSign() {
    String urlString = "jdbc:mysql//localhost:3306";
    String paramName = "paramName";
    String paramValue = "value=123";
    String result =
        ConnectionParameterHelper.addQueryParameterToURL(urlString, paramName, paramValue);
    assertThat(result).isEqualTo(urlString + "?paramName=value%3D123");
  }

  @Test
  void shouldHandleParameterValueWithPercentSign() {
    String urlString = "jdbc:mysql//localhost:3306";
    String paramName = "paramName";
    String paramValue = "25%discount";
    String result =
        ConnectionParameterHelper.addQueryParameterToURL(urlString, paramName, paramValue);
    assertThat(result).isEqualTo(urlString + "?paramName=25%25discount");
  }
}
