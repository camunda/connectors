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
  void shouldCreateQueryParameters_whenNoExistingQueryParameters() throws Exception {
    String urlString = "jdbc:mysql//localhost:3306";
    String paramName = "paramName";
    String paramValue = "paramValue";
    String result =
        ConnectionParameterHelper.addQueryParameterToURL(urlString, paramName, paramValue);
    assertThat(result).isEqualTo(urlString + "?paramName=paramValue");
  }

  @Test
  void shouldNotCreateQueryParameters_whenExistingQueryParameters() throws Exception {
    String urlString = "jdbc:mysql//localhost:3306?existingParam=existingValue";
    String paramName = "paramName";
    String paramValue = "paramValue";
    String result =
        ConnectionParameterHelper.addQueryParameterToURL(urlString, paramName, paramValue);
    assertThat(result).isEqualTo(urlString + "&paramName=paramValue");
  }

  @Test
  void shouldCreateQueryParameters_whenNoParamValue() throws Exception {
    String urlString = "jdbc:mysql//localhost:3306";
    String paramName = "paramName";
    String result = ConnectionParameterHelper.addQueryParameterToURL(urlString, paramName);
    assertThat(result).isEqualTo(urlString + "?paramName");
  }

  @Test
  void shouldCreateQueryParameters_whenNoParamValueAndExistingQueryParameters() throws Exception {
    String urlString = "jdbc:mysql//localhost:3306?existingParam=existingValue";
    String paramName = "paramName";
    String result = ConnectionParameterHelper.addQueryParameterToURL(urlString, paramName);
    assertThat(result).isEqualTo(urlString + "&paramName");
  }

  @Test
  void shouldCreateQueryParametersAfterPath_whenQueryPathExistsAndNoParamValue() throws Exception {
    String urlString = "jdbc:mysql//localhost:3306/database";
    String paramName = "paramName";
    String result = ConnectionParameterHelper.addQueryParameterToURL(urlString, paramName);
    assertThat(result).isEqualTo(urlString + "?paramName");
  }

  @Test
  void shouldCreateQueryParametersAfterPath_whenQueryPathExistsAndQueryParametersExist()
      throws Exception {
    String urlString = "jdbc:mysql//localhost:3306/database?existingParam=existingValue";
    String paramName = "paramName";
    String result = ConnectionParameterHelper.addQueryParameterToURL(urlString, paramName);
    assertThat(result).isEqualTo(urlString + "&paramName");
  }

  @Test
  void shouldCreateQueryParametersAfterPath_whenEmptyPath() throws Exception {
    String urlString = "jdbc:mysql//localhost:3306/";
    String paramName = "paramName";
    String result = ConnectionParameterHelper.addQueryParameterToURL(urlString, paramName);
    assertThat(result).isEqualTo(urlString + "?paramName");
  }

  @Test
  void
      shouldCreateQueryParametersAfterPath_whenQueryPathExistsAndQueryParametersExistAndPasswordHasWeirdChars()
          throws Exception {
    String urlString = "jdbc:mysql//localhost:3306/database?user=test&password=ab#!xij:()_s23";
    String paramName = "paramName";
    String result = ConnectionParameterHelper.addQueryParameterToURL(urlString, paramName);
    assertThat(result).isEqualTo(urlString + "&paramName");
  }
}
