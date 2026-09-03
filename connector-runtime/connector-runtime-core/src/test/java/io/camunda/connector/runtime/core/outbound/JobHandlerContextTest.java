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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.secret.SecretFilter;
import io.camunda.connector.runtime.core.secret.SecretFilter.Secret;
import io.camunda.connector.runtime.core.testutil.classexample.TestClass;
import io.camunda.connector.runtime.core.testutil.classexample.TestClassString;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobHandlerContextTest {

  @Mock private ActivatedJob activatedJob;
  @Mock private SecretProvider secretProvider;
  @Mock private ValidationProvider validationProvider;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private JobHandlerContext jobHandlerContext;

  private void stubVariables(String json) {
    when(activatedJob.getVariables()).thenReturn(json);
  }

  @BeforeEach
  void setUp() {
    jobHandlerContext =
        new JobHandlerContext(
            activatedJob,
            secretProvider,
            validationProvider,
            null,
            objectMapper,
            SecretFilter.allowAll());
  }

  @Test
  void getVariables() {
    stubVariables("{}");
    jobHandlerContext.getJobContext().getVariables();
    verify(activatedJob).getVariables();
  }

  @Test
  void bindVariables_preservesFullDecimalPrecision() {
    stubVariables("{ \"decimal\": 0.1234567890123456789012345 }");
    BigDecimal bound = jobHandlerContext.bindVariables(TestClass.class).decimal;
    assertThat(bound).isEqualByComparingTo(new BigDecimal("0.1234567890123456789012345"));
  }

  @Test
  void bindVariables_preservesTrailingZeroScale() {
    // isEqualByComparingTo alone would pass even if the default node factory silently stripped
    // the trailing zero (1.10 -> 1.1): equals()/isEqualTo is scale-sensitive, compareTo is not.
    stubVariables("{ \"decimal\": 1.10 }");
    BigDecimal bound = jobHandlerContext.bindVariables(TestClass.class).decimal;
    assertThat(bound).isEqualTo(new BigDecimal("1.10"));
  }

  @Test
  void bindVariables_preservesLongBeyondDoublePrecision() {
    stubVariables("{ \"longValue\": 9007199254740993 }");
    assertThat(jobHandlerContext.bindVariables(TestClass.class).longValue)
        .isEqualTo(9007199254740993L);
  }

  @Test
  void getVariables_preservesDecimalText() {
    stubVariables(
        "{ \"a\": 0.00000001, \"b\": 1.10, \"c\": 0.1234567890123456789012345, \"d\":"
            + " 9007199254740993 }");
    String variables = jobHandlerContext.getJobContext().getVariables();
    assertThat(variables)
        .contains("0.00000001", "1.10", "0.1234567890123456789012345")
        .contains("9007199254740993");
  }

  @Test
  void getVariables_fallsBackToScientificNotationForOutOfRangeDecimalScale() {
    // Jackson accepts plain output only for a BigDecimal scale within +-9999, and both of these
    // parse into a DecimalNode outside it. Serializing must fall back to scientific notation, as
    // the raw job JSON string did, rather than throwing.
    stubVariables("{ \"tiny\": 1e-10000, \"huge\": 1e+10001 }");
    assertThat(jobHandlerContext.getJobContext().getVariables())
        .contains("1E-10000")
        .contains("1E+10001");
  }

  @Test
  void bindVariables_success() {
    String json = "{ \"integer\": 3}";
    stubVariables(json);
    assertThat(jobHandlerContext.bindVariables(TestClass.class).integer).isEqualTo(3);
  }

  @Test
  void bindVariables_failedSecretAreBounded() {
    String json = "{ \"integer\": \"{{secrets.FOO}}\" }";
    stubVariables(json);
    when(secretProvider.getSecret(eq("FOO"), any())).thenReturn("secret");
    Exception thrown =
        assertThrows(
            ConnectorInputException.class, () -> jobHandlerContext.bindVariables(TestClass.class));
    assertThat(thrown.getMessage())
        .isEqualTo("Json object contains an invalid field: integer. It Must be `Integer`");
  }

  @Test
  void bindVariables_successSecretAreBounded() {
    // Legacy secret substitution now runs after JSON parsing (over the parsed tree), so a
    // placeholder must sit inside a JSON string like any other value — an unquoted placeholder for
    // a non-string field is no longer valid JSON and can never reach substitution.
    String json = "{ \"integer\": \"{{secrets.FOO}}\" }";
    stubVariables(json);
    when(secretProvider.getSecret(eq("FOO"), any())).thenReturn("1");
    assertThat(jobHandlerContext.bindVariables(TestClass.class).integer).isEqualTo(1);
  }

  @Test
  void bindVariables_successJsonSecretAreEscaped() {
    String json = "{ \"value\": \"{{secrets.FOO}}\" }";
    stubVariables(json);
    when(secretProvider.getSecret(eq("FOO"), any())).thenReturn("{\"key\": \"secret\"}");
    assertThat(jobHandlerContext.bindVariables(TestClassString.class).value)
        .isEqualTo("{\"key\": \"secret\"}");
  }

  @Test
  void bindVariables_successJsonSecretAreEscapedAndCarriageReturnEscaped() {
    String json = "{ \"value\": \"{{secrets.FOO}}\" }";
    stubVariables(json);
    when(secretProvider.getSecret(eq("FOO"), any())).thenReturn("{\"key\": \n\"secret\"}");
    assertThat(jobHandlerContext.bindVariables(TestClassString.class).value)
        .isEqualTo("{\"key\": \n\"secret\"}");
  }

  @Test
  void bindVariables_successJsonSecretAreEscapedAndNullByteRemoved() {
    String json = "{ \"value\": \"{{secrets.FOO}}\" }";
    stubVariables(json);
    when(secretProvider.getSecret(eq("FOO"), any())).thenReturn("{\"key\": \"sec\0ret\"}");
    assertThat(jobHandlerContext.bindVariables(TestClassString.class).value)
        .isEqualTo("{\"key\": \"sec\0ret\"}");
  }

  @Test
  void bindVariables_secretIsNotAvailable() {
    String json = "{ \"integer\": \"{{secrets.FOO2}}\" }";
    stubVariables(json);
    when(secretProvider.getSecret(eq("FOO2"), any())).thenReturn(null);
    assertThrows(
        ConnectorInputException.class, () -> jobHandlerContext.bindVariables(TestClass.class));
  }

  @Test
  void bindVariables_successStringSecretAreEscapedAndCarriageReturnEscaped() {
    String json = "{ \"value\": \"{{secrets.FOO}}\" }";
    stubVariables(json);
    when(secretProvider.getSecret(eq("FOO"), any())).thenReturn("Hello \n World");
    assertThat(jobHandlerContext.bindVariables(TestClassString.class).value)
        .isEqualTo("Hello \n World");
  }

  @Test
  void bindVariables_successStringSecretAreEscapedAndQuoteEscaped() {
    String json = "{ \"value\": \"{{secrets.FOO}}\" }";
    stubVariables(json);
    when(secretProvider.getSecret(eq("FOO"), any())).thenReturn("Hello \" World");
    assertThat(jobHandlerContext.bindVariables(TestClassString.class).value)
        .isEqualTo("Hello \" World");
  }

  @Test
  void bindVariables_successStringSecretAreEscapedAndNullByteEscaped() {
    String json = "{ \"value\": \"{{secrets.FOO}}\" }";
    stubVariables(json);
    when(secretProvider.getSecret(eq("FOO"), any())).thenReturn("Hello \0 World");
    assertThat(jobHandlerContext.bindVariables(TestClassString.class).value)
        .isEqualTo("Hello \0 World");
  }

  @Test
  void bindVariables_nullValue() {
    String json = "{ \"integer\": null}";
    stubVariables(json);
    assertThat(jobHandlerContext.bindVariables(TestClass.class).integer).isEqualTo(null);
  }

  @Test
  void bindVariables_invalidFormat() {
    String json = "{ \"integer\": \"hello\"}";
    stubVariables(json);
    Exception thrown =
        assertThrows(
            ConnectorInputException.class, () -> jobHandlerContext.bindVariables(TestClass.class));

    assertThat(thrown.getMessage())
        .isEqualTo("Json object contains an invalid field: integer. It Must be `Integer`");
  }

  @Test
  void bindVariables_invalidFormatNull() {
    String json = "{ \"invalid\": null }";
    stubVariables(json);
    Exception thrown =
        assertThrows(
            ConnectorInputException.class, () -> jobHandlerContext.bindVariables(TestClass.class));

    assertThat(thrown.getMessage()).isEqualTo("Json object contains an invalid field: invalid");
  }

  @Test
  void bindVariables_invalidParsing() {
    String json = "{ \"integer\" hello\"}";
    stubVariables(json);
    Exception thrown =
        assertThrows(
            ConnectorInputException.class, () -> jobHandlerContext.bindVariables(TestClass.class));

    assertThat(thrown.getMessage()).isEqualTo("This is not a JSON object");
  }

  @Test
  void bindVariables_invalidFormatObject() {
    String json = "{ \"integer\": \"{ \\\"hello\\\" : 3}\" }";
    stubVariables(json);
    Exception thrown =
        assertThrows(
            ConnectorInputException.class, () -> jobHandlerContext.bindVariables(TestClass.class));

    assertThat(thrown.getMessage())
        .isEqualTo("Json object contains an invalid field: integer. It Must be `Integer`");
  }

  @Test
  void bindVariables_emptyString() {
    String json = "";
    stubVariables(json);
    Exception thrown =
        assertThrows(
            ConnectorInputException.class, () -> jobHandlerContext.bindVariables(TestClass.class));

    assertThat(thrown.getMessage()).isEqualTo("No content to map due to end-of-input");
  }

  @Test
  void bindVariables_emptyArray() {
    // Job variables are always a JSON object in Zeebe/Camunda 8, and the tree walk that secret
    // substitution runs first requires one, so an array is rejected before it ever reaches type
    // binding.
    String json = "[]";
    stubVariables(json);
    Exception thrown =
        assertThrows(
            ConnectorInputException.class, () -> jobHandlerContext.bindVariables(TestClass.class));

    assertThat(thrown.getMessage()).isEqualTo("This is not a JSON object");
  }

  @Test
  void bindVariables_invalidFormatArray() {
    String json = "{ \"integer\": [\"hello\"] }";
    stubVariables(json);
    Exception thrown =
        assertThrows(
            ConnectorInputException.class, () -> jobHandlerContext.bindVariables(TestClass.class));

    assertThat(thrown.getMessage())
        .isEqualTo(
            "Cannot deserialize value of type `java.lang.Integer` from Array value (token `JsonToken.START_ARRAY`)");
  }

  @Test
  void bindVariables_undeclaredSecretIsLeftUnresolvedAndProviderIsNeverCalled() {
    // Every other test in this file uses SecretFilter.allowAll(), which never exercises the
    // security boundary #7568 introduced: nothing here previously verified that a restrictive
    // filter actually blocks resolution through bindVariables.
    var restrictiveContext =
        new JobHandlerContext(
            activatedJob,
            secretProvider,
            validationProvider,
            null,
            objectMapper,
            SecretFilter.allowOnly(List.of(new Secret("AUTH", List.of()))));
    String json = "{ \"value\": \"{{secrets.UNDECLARED}}\" }";
    stubVariables(json);

    var bound = restrictiveContext.bindVariables(TestClassString.class);

    assertThat(bound.value).isEqualTo("{{secrets.UNDECLARED}}");
    verify(secretProvider, never()).getSecret(eq("UNDECLARED"), any());
  }
}
