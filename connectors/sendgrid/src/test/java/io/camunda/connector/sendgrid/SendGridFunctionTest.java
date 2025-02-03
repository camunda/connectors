/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.sendgrid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import io.camunda.client.api.response.DocumentMetadata;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.sendgrid.model.SendGridRequest;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.document.Document;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

public class SendGridFunctionTest extends BaseTest {

  private static final String TEMPLATE_ID_JSON_NAME = "template_id";
  private static final String CONTENT_JSON_NAME = "content";
  private static final String CONTENT_TYPE_JSON_NAME = "type";
  private static final String CONTENT_VALUE_JSON_NAME = "value";
  private static final String SUBJECT_JSON_NAME = "subject";
  private static final String FROM_JSON_NAME = "from";
  private static final String TO_JSON_NAME = "to";
  private static final String PERSONALIZATION_JSON_NAME = "personalizations";
  private static final String NAME_JSON_NAME = "name";
  private static final String EMAIL_JSON_NAME = "email";
  private static final String ATTACHMENTS_JSON_NAME = "attachments";
  private static final String ATTACHMENTS_FILE_NAME_JSON_NAME = "filename";

  private OutboundConnectorContext context;
  private SendGridFunction function;
  private Response sendGridResponse;
  private SendGrid sendGridMock;
  private ArgumentCaptor<Request> requestArgumentCaptor;
  private OutboundConnectorContextBuilder contextBuilder;

  @BeforeEach
  public void init() throws IOException {
    contextBuilder = getContextBuilderWithSecrets();

    SendGridErrors.SendGridError sendGridError = new SendGridErrors.SendGridError("error msg");
    SendGridErrors sendGridErrors = new SendGridErrors(List.of(sendGridError));

    sendGridResponse = new Response();
    sendGridResponse.setBody(objectMapper.writeValueAsString(sendGridErrors));
    sendGridResponse.setStatusCode(202);

    SendGridClientSupplier sendGridSupplierMock = mock(SendGridClientSupplier.class);
    sendGridMock = mock(SendGrid.class);

    when(sendGridSupplierMock.sendGrid(any())).thenReturn(sendGridMock);
    when(sendGridMock.api(any())).thenReturn(sendGridResponse);
    function = new SendGridFunction(sendGridSupplierMock);

    requestArgumentCaptor = ArgumentCaptor.forClass(Request.class);
  }

  @ParameterizedTest(name = " # {index} , test statusCode = {0}")
  @ValueSource(ints = {100, 200, 201, 203, 303, 400, 404})
  public void execute_shouldThrowExceptionIfResponseStatusCodeIsNot202(int statusCode) {
    // ignore validate and replace secrets, test only result cases
    context = spy(OutboundConnectorContext.class);
    when(context.bindVariables(any())).thenReturn(mock(SendGridRequest.class));
    // Given response with bad status
    sendGridResponse.setStatusCode(statusCode);
    // When and then
    IllegalArgumentException exceptionThrown =
        Assertions.assertThrows(IllegalArgumentException.class, () -> function.execute(context));

    assertThat(exceptionThrown)
        .hasMessageContaining("SendGrid returned the following errors:", statusCode);
  }

  @ParameterizedTest(name = " # {index} , test statusCode = {0}")
  @ValueSource(ints = {202})
  public void execute_shouldReturnNullIfResponseStatusCodeIs202(int statusCode) throws Exception {
    // ignore validate and replace secrets, test only result cases
    context = spy(OutboundConnectorContext.class);
    when(context.bindVariables(any())).thenReturn(mock(SendGridRequest.class));
    // Given
    sendGridResponse.setStatusCode(statusCode);
    // When
    Object execute = function.execute(context);
    // Then no exception and result is null
    assertThat(execute).isNotNull();
  }

  @ParameterizedTest(
      name = "Should create request with mail and expected data. Test case # {index}")
  @MethodSource("successSendMailWithContentRequestCases")
  public void execute_shouldCreateRequestWithMailAndExpectedData(String input) throws Exception {
    // Given
    context = contextBuilder.variables(input).build();

    var contextWithMocketDocument = mock(OutboundConnectorContext.class);

    var requestWithMockedDocument = prepareRequestWithDocumentMock(context);

    when(contextWithMocketDocument.bindVariables(any())).thenReturn(requestWithMockedDocument);

    // When
    function.execute(contextWithMocketDocument);
    verify(sendGridMock).api(requestArgumentCaptor.capture());
    // Then we have POST request with mail participants,
    Request requestValue = requestArgumentCaptor.getValue();
    assertThat(requestValue.getMethod()).isEqualTo(Method.POST);

    var requestJsonObject = objectMapper.readTree(requestValue.getBody());
    var from = requestJsonObject.get(FROM_JSON_NAME);

    var to =
        requestJsonObject.withObject(
            JsonPointer.valueOf("/" + PERSONALIZATION_JSON_NAME + "/0/" + TO_JSON_NAME + "/0"));

    var array = (ArrayNode) requestJsonObject.get(ATTACHMENTS_JSON_NAME);
    String attachmentName = array.get(0).get(ATTACHMENTS_FILE_NAME_JSON_NAME).textValue();

    assertThat(from.get(NAME_JSON_NAME).textValue()).isEqualTo(ActualValue.SENDER_NAME);
    assertThat(from.get(EMAIL_JSON_NAME).textValue()).isEqualTo(ActualValue.SENDER_EMAIL);
    assertThat(to.get(NAME_JSON_NAME).textValue()).isEqualTo(ActualValue.RECEIVER_NAME);
    assertThat(to.get(EMAIL_JSON_NAME).textValue()).isEqualTo(ActualValue.RECEIVER_EMAIL);
    assertThat(attachmentName).isEqualTo(ActualValue.ATTACHED_FILE_NAME);
  }

  @ParameterizedTest(name = "Should send mail with template. Test case # {index}")
  @MethodSource("successSendMailByTemplateRequestCases")
  public void execute_shouldSendMailByTemplateIfTemplateExist(String input) throws Exception {
    // Given
    context = contextBuilder.variables(input).build();

    var contextWithMocketDocument = mock(OutboundConnectorContext.class);

    var requestWithMockedDocument = prepareRequestWithDocumentMock(context);

    when(contextWithMocketDocument.bindVariables(any())).thenReturn(requestWithMockedDocument);

    // When
    function.execute(contextWithMocketDocument);
    verify(sendGridMock).api(requestArgumentCaptor.capture());
    // Then we have 'template_id' in sendGridRequest with expected ID and 'content' is not exist
    Request requestValue = requestArgumentCaptor.getValue();

    var requestJsonObject = objectMapper.readTree(requestValue.getBody());

    var array = (ArrayNode) requestJsonObject.get(ATTACHMENTS_JSON_NAME);
    String attachmentName = array.get(0).get(ATTACHMENTS_FILE_NAME_JSON_NAME).textValue();
    assertThat(requestJsonObject.get(TEMPLATE_ID_JSON_NAME).textValue())
        .isEqualTo(ActualValue.Template.ID);
    assertThat(requestJsonObject.has(CONTENT_JSON_NAME)).isFalse();
    assertThat(attachmentName).isEqualTo(ActualValue.ATTACHED_FILE_NAME);
  }

  @ParameterizedTest(name = "Should send mail with content. Test case # {index}")
  @MethodSource("successSendMailWithContentRequestCases")
  public void execute_shouldSendMailIfContentExist(String input) throws Exception {
    // Given
    context = contextBuilder.variables(input).build();

    var contextWithMocketDocument = mock(OutboundConnectorContext.class);

    var requestWithMockedDocument = prepareRequestWithDocumentMock(context);

    when(contextWithMocketDocument.bindVariables(any())).thenReturn(requestWithMockedDocument);

    // When
    function.execute(contextWithMocketDocument);

    verify(sendGridMock).api(requestArgumentCaptor.capture());
    // Then we have 'content' in sendGridRequest with expected data and template ID is not exist
    var requestJsonObject = objectMapper.readTree(requestArgumentCaptor.getValue().getBody());

    assertThat(requestJsonObject.get(SUBJECT_JSON_NAME).textValue())
        .isEqualTo(ActualValue.Content.SUBJECT);
    var content =
        requestJsonObject.get(CONTENT_JSON_NAME).withObject(JsonPointer.empty().appendIndex(0));
    assertThat(content.get(CONTENT_TYPE_JSON_NAME).textValue()).isEqualTo(ActualValue.Content.TYPE);
    assertThat(content.get(CONTENT_VALUE_JSON_NAME).textValue())
        .isEqualTo(ActualValue.Content.VALUE);

    assertThat(requestJsonObject.has(TEMPLATE_ID_JSON_NAME)).isFalse();
  }

  private SendGridRequest prepareRequestWithDocumentMock(OutboundConnectorContext context) {
    var request = context.bindVariables(SendGridRequest.class);

    var document = mock(Document.class);
    var documentMetadata = mock(DocumentMetadata.class);
    when(document.metadata()).thenReturn(documentMetadata);

    String fileName = request.getAttachments().getFirst().metadata().getFileName();
    when(documentMetadata.getFileName()).thenReturn(fileName);
    when(document.asInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));

    request.setAttachments(List.of(document));
    return request;
  }
}
