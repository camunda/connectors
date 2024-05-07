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
}
