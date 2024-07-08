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
package io.camunda.connector.runtime.core.outbound;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.testutil.classexample.TestClass;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobHandlerContextTest {

  @Mock private ActivatedJob activatedJob;
  @Mock private SecretProvider secretProvider;
  @Spy private ObjectMapper objectMapper = new ObjectMapper();
  @Mock private ValidationProvider validationProvider;

  @InjectMocks private JobHandlerContext jobHandlerContext;

  @Test
  void getVariables() {
    when(activatedJob.getVariables()).thenReturn("{}");
    jobHandlerContext.getJobContext().getVariables();
    verify(activatedJob).getVariables();
  }

  @Test
  void bindVariables_success() {
    String json = "{ \"integer\": 3}";
    when(activatedJob.getVariables()).thenReturn(json);
    assertThat(jobHandlerContext.bindVariables(TestClass.class).integer).isEqualTo(3);
  }

  @Test
  void bindVariables_nullValue() {
    String json = "{ \"integer\": null}";
    when(activatedJob.getVariables()).thenReturn(json);
    assertThat(jobHandlerContext.bindVariables(TestClass.class).integer).isEqualTo(null);
  }

  @Test
  void bindVariables_invalidFormat() {
    String json = "{ \"integer\": \"hello\"}";
    when(activatedJob.getVariables()).thenReturn(json);
    Exception thrown =
        assertThrows(
            ConnectorException.class, () -> jobHandlerContext.bindVariables(TestClass.class));

    assertThat(thrown.getMessage())
        .isEqualTo("Json object contains an invalid field: integer. It Must be `Integer`");
  }

  @Test
  void bindVariables_invalidFormatNull() {
    String json = "{ \"invalid\": null }";
    when(activatedJob.getVariables()).thenReturn(json);
    Exception thrown =
        assertThrows(
            ConnectorException.class, () -> jobHandlerContext.bindVariables(TestClass.class));

    assertThat(thrown.getMessage()).isEqualTo("Json object contains an invalid field: invalid");
  }

  @Test
  void bindVariables_invalidParsing() {
    String json = "{ \"integer\" hello\"}";
    when(activatedJob.getVariables()).thenReturn(json);
    Exception thrown =
        assertThrows(
            ConnectorException.class, () -> jobHandlerContext.bindVariables(TestClass.class));

    assertThat(thrown.getMessage()).isEqualTo("This is not a JSON object");
  }

  @Test
  void bindVariables_invalidFormatObject() {
    String json = "{ \"integer\": \"{ \"hello\" : 3\"}";
    when(activatedJob.getVariables()).thenReturn(json);
    Exception thrown =
        assertThrows(
            ConnectorException.class, () -> jobHandlerContext.bindVariables(TestClass.class));

    assertThat(thrown.getMessage())
        .isEqualTo("Json object contains an invalid field: integer. It Must be `Integer`");
  }

  @Test
  void bindVariables_emptyString() {
    String json = "";
    when(activatedJob.getVariables()).thenReturn(json);
    Exception thrown =
        assertThrows(
            ConnectorException.class, () -> jobHandlerContext.bindVariables(TestClass.class));

    assertThat(thrown.getMessage()).isEqualTo("No content to map due to end-of-input");
  }

  @Test
  void bindVariables_emptyArray() {
    String json = "[]";
    when(activatedJob.getVariables()).thenReturn(json);
    Exception thrown =
        assertThrows(
            ConnectorException.class, () -> jobHandlerContext.bindVariables(TestClass.class));

    assertThat(thrown.getMessage())
        .isEqualTo(
            "Cannot deserialize value of type `io.camunda.connector.runtime.core.testutil.classexample.TestClass` from Array value (token `JsonToken.START_ARRAY`)");
  }

  @Test
  void bindVariables_invalidFormatArray() {
    String json = "{ \"integer\": [\"hello\"] }";
    when(activatedJob.getVariables()).thenReturn(json);
    Exception thrown =
        assertThrows(
            ConnectorException.class, () -> jobHandlerContext.bindVariables(TestClass.class));

    assertThat(thrown.getMessage())
        .isEqualTo(
            "Cannot deserialize value of type `java.lang.Integer` from Array value (token `JsonToken.START_ARRAY`)");
  }
}
