/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.s3.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.aws.s3.model.request.DeleteObject;
import io.camunda.connector.aws.s3.model.request.DownloadObject;
import io.camunda.connector.aws.s3.model.request.S3Action;
import io.camunda.connector.aws.s3.model.request.UploadObject;
import io.camunda.connector.aws.s3.model.response.DeleteResponse;
import io.camunda.connector.aws.s3.model.response.DownloadResponse;
import io.camunda.connector.aws.s3.model.response.Element;
import io.camunda.connector.aws.s3.model.response.UploadResponse;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import java.io.IOException;
import java.util.Base64;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3ExecutorTest {

  @Test
  void executeDeleteAction() {
    S3Client s3Client = mock(S3Client.class);
    Function<DocumentCreationRequest, Document> function = doc -> mock(Document.class);
    S3Executor executor = new S3Executor(s3Client, function);
    S3Action s3Action = new DeleteObject("test", "key");

    Object object = executor.execute(s3Action);

    verify(s3Client, times(1)).deleteObject(any(DeleteObjectRequest.class));
    assertInstanceOf(DeleteResponse.class, object);
  }

  @Test
  void executeUploadAction() {
    S3Client s3Client = mock(S3Client.class);
    Function<DocumentCreationRequest, Document> function = doc -> mock(Document.class);
    S3Executor executor = new S3Executor(s3Client, function);
    Document document = mock(Document.class, RETURNS_DEEP_STUBS);
    S3Action s3Action = new UploadObject("test", "key", document);

    when(document.metadata().getSize()).thenReturn(42L);
    when(document.metadata().getContentType()).thenReturn("application/octet-stream");

    Object object = executor.execute(s3Action);

    verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    assertInstanceOf(UploadResponse.class, object);
  }

  @Test
  void executeDownloadAsDocumentAction() {

    S3Client s3Client = mock(S3Client.class);
    Function<DocumentCreationRequest, Document> function = doc -> mock(Document.class);
    S3Executor executor = new S3Executor(s3Client, function);
    ResponseInputStream<GetObjectResponse> responseInputStream = mock(ResponseInputStream.class);
    GetObjectResponse getObjectResponse = mock(GetObjectResponse.class);
    S3Action s3Action = new DownloadObject("test", "key", true);

    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseInputStream);
    when(responseInputStream.response()).thenReturn(getObjectResponse);
    when(getObjectResponse.contentType()).thenReturn("application/octet-stream");
    Object object = executor.execute(s3Action);

    verify(s3Client, times(1)).getObject(any(GetObjectRequest.class));
    assertInstanceOf(DownloadResponse.class, object);
    assertInstanceOf(Element.DocumentContent.class, ((DownloadResponse) object).element());
  }

  @Test
  void executeDownloadAsTextContentAction() throws IOException {

    S3Client s3Client = mock(S3Client.class);
    Function<DocumentCreationRequest, Document> function = doc -> mock(Document.class);
    S3Executor executor = new S3Executor(s3Client, function);
    ResponseInputStream<GetObjectResponse> responseInputStream = mock(ResponseInputStream.class);
    GetObjectResponse getObjectResponse = mock(GetObjectResponse.class);
    S3Action s3Action = new DownloadObject("test", "key", false);

    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseInputStream);
    when(responseInputStream.response()).thenReturn(getObjectResponse);
    when(responseInputStream.readAllBytes()).thenReturn("Hello World".getBytes());
    when(getObjectResponse.contentLength()).thenReturn(234L);
    when(getObjectResponse.contentType()).thenReturn("text/plain");
    Object object = executor.execute(s3Action);

    verify(s3Client, times(1)).getObject(any(GetObjectRequest.class));
    assertInstanceOf(DownloadResponse.class, object);
    assertNotNull(((DownloadResponse) object).element());
    assertInstanceOf(Element.StringContent.class, ((DownloadResponse) object).element());
    assertEquals(
        "Hello World", ((Element.StringContent) ((DownloadResponse) object).element()).content());
  }

  @Test
  void executeDownloadAsJsonContentAction() throws IOException {

    S3Client s3Client = mock(S3Client.class);
    Function<DocumentCreationRequest, Document> function = doc -> mock(Document.class);
    S3Executor executor = new S3Executor(s3Client, function);
    ResponseInputStream<GetObjectResponse> responseInputStream = mock(ResponseInputStream.class);
    GetObjectResponse getObjectResponse = mock(GetObjectResponse.class);
    S3Action s3Action = new DownloadObject("test", "key", false);

    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseInputStream);
    when(responseInputStream.response()).thenReturn(getObjectResponse);
    when(responseInputStream.readAllBytes()).thenReturn("{ \"Hello\" : \"World\" }".getBytes());
    when(getObjectResponse.contentLength()).thenReturn(234L);
    when(getObjectResponse.contentType()).thenReturn("application/json");
    Object object = executor.execute(s3Action);

    verify(s3Client, times(1)).getObject(any(GetObjectRequest.class));
    assertInstanceOf(DownloadResponse.class, object);
    DownloadResponse downloadResponse = (DownloadResponse) object;
    assertNotNull(downloadResponse.element());
    assertEquals(
        "World",
        ((JsonNode) ((Element.JsonContent) downloadResponse.element()).content())
            .get("Hello")
            .asText());
  }

  @Test
  void executeDownloadAsBase64BytesContentAction() throws IOException {

    S3Client s3Client = mock(S3Client.class);
    Function<DocumentCreationRequest, Document> function = doc -> mock(Document.class);
    S3Executor executor = new S3Executor(s3Client, function);
    ResponseInputStream<GetObjectResponse> responseInputStream = mock(ResponseInputStream.class);
    GetObjectResponse getObjectResponse = mock(GetObjectResponse.class);
    S3Action s3Action = new DownloadObject("test", "key", false);

    when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseInputStream);
    when(responseInputStream.response()).thenReturn(getObjectResponse);
    when(responseInputStream.readAllBytes()).thenReturn("Hello".getBytes());
    when(getObjectResponse.contentLength()).thenReturn(234L);
    when(getObjectResponse.contentType()).thenReturn("application/octet-stream");
    Object object = executor.execute(s3Action);

    verify(s3Client, times(1)).getObject(any(GetObjectRequest.class));
    assertInstanceOf(DownloadResponse.class, object);
    DownloadResponse downloadResponse = (DownloadResponse) object;
    assertNotNull(downloadResponse.element());
    assertEquals(
        Base64.getEncoder().encodeToString("Hello".getBytes()),
        ((Element.StringContent) downloadResponse.element()).content());
  }
}
