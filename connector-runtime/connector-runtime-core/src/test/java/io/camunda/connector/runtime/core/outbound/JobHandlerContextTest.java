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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.api.command.InternalClientException;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.DocumentReturnChoice;
import io.camunda.connector.api.document.DocumentReturnFormat;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.secret.SecretFilter;
import io.camunda.connector.runtime.core.testutil.classexample.TestClass;
import io.camunda.connector.runtime.core.testutil.classexample.TestClassString;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobHandlerContextTest {

  @Mock private ActivatedJob activatedJob;
  @Mock private SecretProvider secretProvider;
  @Mock private ValidationProvider validationProvider;
  @Mock private DocumentFactory documentFactory;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private JobHandlerContext jobHandlerContext;

  /**
   * Mirrors {@code CamundaObjectMapper.fromJson}, which is what the real {@code ActivatedJob}
   * delegates {@code getVariablesAsType} to: malformed JSON surfaces as an {@link
   * InternalClientException} wrapping the original Jackson exception, not the Jackson exception
   * itself.
   */
  private static JsonNode parseLikeClient(String json) {
    try {
      return new ObjectMapper().readValue(json, JsonNode.class);
    } catch (IOException e) {
      throw new InternalClientException(
          String.format("Failed to deserialize json '%s' to class '%s'", json, JsonNode.class), e);
    }
  }

  private void stubVariables(String json) {
    when(activatedJob.getVariablesAsType(JsonNode.class)).thenAnswer(inv -> parseLikeClient(json));
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
    verify(activatedJob).getVariablesAsType(JsonNode.class);
  }

  @Test
  void getLeaseToken() {
    when(activatedJob.getLeaseToken()).thenReturn("lease-token-1");
    assertThat(jobHandlerContext.getJobContext().getLeaseToken()).isEqualTo("lease-token-1");
  }

  @Test
  void getLeaseToken_nullWhenJobActivatedWithoutLease() {
    when(activatedJob.getLeaseToken()).thenReturn(null);
    assertThat(jobHandlerContext.getJobContext().getLeaseToken()).isNull();
  }

  @Test
  void readDocumentReturnFormat_absentReturnsEmptyForOlderTemplates() {
    // Older templates do not send `documentReturnFormat`. Reading it must not throw the way
    // job.getVariable(...) would, so the connector can fall through to its legacy flow.
    when(activatedJob.getVariablesAsMap()).thenReturn(Map.of("someOtherVar", "x"));

    assertThat(jobHandlerContext.readDocumentReturnFormat()).isEmpty();
  }

  @Test
  void readDocumentReturnFormat_parsesChoiceAndEncodingWhenPresent() {
    when(activatedJob.getVariablesAsMap())
        .thenReturn(
            Map.of("documentReturnFormat", Map.of("choice", "TEXT", "encoding", "ISO-8859-1")));

    Optional<DocumentReturnFormat> format = jobHandlerContext.readDocumentReturnFormat();

    assertThat(format).isPresent();
    assertThat(format.get().choice()).isEqualTo(DocumentReturnChoice.TEXT);
    assertThat(format.get().encoding()).isEqualTo("ISO-8859-1");
  }

  @Test
  void readDocumentReturnFormat_rejectsInvalidChoice() {
    when(activatedJob.getVariablesAsMap())
        .thenReturn(Map.of("documentReturnFormat", Map.of("choice", "BOGUS")));

    assertThatThrownBy(() -> jobHandlerContext.readDocumentReturnFormat())
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("DOCUMENT, TEXT, JSON");
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
  void bindVariables_secretContextCarriesTheJobsPhysicalTenantId() {
    stubVariables("{ \"integer\": \"{{secrets.FOO}}\" }");
    when(activatedJob.getTenantId()).thenReturn("my-tenant");
    when(activatedJob.getBpmnProcessId()).thenReturn("my-process");
    when(activatedJob.getPhysicalTenantId()).thenReturn("engine-1");
    when(secretProvider.getSecret(eq("FOO"), any())).thenReturn("1");

    jobHandlerContext.bindVariables(TestClass.class);

    var secretContext = ArgumentCaptor.forClass(SecretContext.class);
    verify(secretProvider).getSecret(eq("FOO"), secretContext.capture());
    assertThat(secretContext.getValue())
        .isEqualTo(new SecretContext("my-tenant", "my-process", "engine-1"));
  }

  @Test
  void bindVariables_secretContextHasNoPhysicalTenantIdWhenTheJobReportsNone() {
    // clusters that predate multi-engine support report an empty physical tenant
    stubVariables("{ \"integer\": \"{{secrets.FOO}}\" }");
    when(activatedJob.getPhysicalTenantId()).thenReturn("");
    when(secretProvider.getSecret(eq("FOO"), any())).thenReturn("1");

    jobHandlerContext.bindVariables(TestClass.class);

    var secretContext = ArgumentCaptor.forClass(SecretContext.class);
    verify(secretProvider).getSecret(eq("FOO"), secretContext.capture());
    assertThat(secretContext.getValue().physicalTenantId()).isNull();
  }

  @Test
  void create_stampsTheJobsPhysicalTenantIdWhenRequestHasNone() {
    when(activatedJob.getPhysicalTenantId()).thenReturn("tenant-a");
    var contextWithDocumentFactory =
        new JobHandlerContext(
            activatedJob,
            secretProvider,
            validationProvider,
            documentFactory,
            objectMapper,
            SecretFilter.allowAll());
    var request =
        DocumentCreationRequest.from(new ByteArrayInputStream("hello".getBytes())).build();

    contextWithDocumentFactory.create(request);

    var captor = ArgumentCaptor.forClass(DocumentCreationRequest.class);
    verify(documentFactory).create(captor.capture());
    assertThat(captor.getValue().physicalTenantId()).isEqualTo("tenant-a");
  }

  @Test
  void create_neverOverridesAnExplicitlySetPhysicalTenantId() {
    var contextWithDocumentFactory =
        new JobHandlerContext(
            activatedJob,
            secretProvider,
            validationProvider,
            documentFactory,
            objectMapper,
            SecretFilter.allowAll());
    var request =
        DocumentCreationRequest.from(new ByteArrayInputStream("hello".getBytes()))
            .physicalTenantId("explicit-tenant")
            .build();

    contextWithDocumentFactory.create(request);

    var captor = ArgumentCaptor.forClass(DocumentCreationRequest.class);
    verify(documentFactory).create(captor.capture());
    assertThat(captor.getValue().physicalTenantId()).isEqualTo("explicit-tenant");
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
}
