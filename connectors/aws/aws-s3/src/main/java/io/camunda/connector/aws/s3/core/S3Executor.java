/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.s3.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.aws.CredentialsProviderSupportV2;
import io.camunda.connector.aws.s3.model.request.*;
import io.camunda.connector.aws.s3.model.response.DeleteResponse;
import io.camunda.connector.aws.s3.model.response.DownloadResponse;
import io.camunda.connector.aws.s3.model.response.Element;
import io.camunda.connector.aws.s3.model.response.UploadResponse;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class S3Executor {

  private static final Logger log = LoggerFactory.getLogger(S3Executor.class);
  private final S3Client s3Client;
  private final Function<DocumentCreationRequest, Document> createDocument;

  public S3Executor(S3Client s3Client, Function<DocumentCreationRequest, Document> createDocument) {
    this.s3Client = s3Client;
    this.createDocument = createDocument;
  }

  public static S3Executor create(
      S3Request s3Request, Function<DocumentCreationRequest, Document> createDocument) {
    return new S3Executor(
        S3Client.builder()
            .credentialsProvider(CredentialsProviderSupportV2.credentialsProvider(s3Request))
            .region(Region.of(s3Request.getConfiguration().region()))
            .build(),
        createDocument);
  }

  public Object execute(S3Action s3Action) {
    return switch (s3Action) {
      case DeleteObject deleteObject -> delete(deleteObject);
      case DownloadObject downloadObject -> download(downloadObject);
      case UploadObject uploadObject -> upload(uploadObject);
    };
  }

  private Object upload(UploadObject uploadObject) {
    Long contentLength = uploadObject.document().metadata().getSize();
    String contentType = uploadObject.document().metadata().getContentType();

    PutObjectRequest putObjectRequest =
        PutObjectRequest.builder()
            .bucket(uploadObject.bucket())
            .key(
                Optional.ofNullable(uploadObject.key())
                    .orElse(uploadObject.document().metadata().getFileName()))
            .contentLength(contentLength)
            .contentType(contentType)
            .build();

    this.s3Client.putObject(
        putObjectRequest,
        RequestBody.fromInputStream(uploadObject.document().asInputStream(), contentLength));

    return new UploadResponse(
        uploadObject.bucket(),
        uploadObject.key(),
        String.format("https://%s.s3.amazonaws.com/%s", uploadObject.bucket(), uploadObject.key()));
  }

  private DownloadResponse download(DownloadObject downloadObject) {
    GetObjectRequest getObjectRequest =
        GetObjectRequest.builder()
            .bucket(downloadObject.bucket())
            .key(downloadObject.key())
            .build();

    ResponseInputStream<GetObjectResponse> getObjectResponse =
        this.s3Client.getObject(getObjectRequest);

    if (!downloadObject.asFile()) {
      try {
        return retrieveResponseWithContent(
            downloadObject.bucket(), downloadObject.key(), getObjectResponse);
      } catch (IOException e) {
        log.error("An error occurred while trying to read and parse the downloaded file", e);
        throw new RuntimeException(e);
      }
    } else {
      return this.createDocument
          .andThen(
              document ->
                  new DownloadResponse(
                      downloadObject.bucket(),
                      downloadObject.key(),
                      new Element.DocumentContent(document)))
          .apply(
              DocumentCreationRequest.from(getObjectResponse)
                  .contentType(getObjectResponse.response().contentType())
                  .fileName(downloadObject.key())
                  .build());
    }
  }

  private DownloadResponse retrieveResponseWithContent(
      String bucket, String key, ResponseInputStream<GetObjectResponse> responseResponseInputStream)
      throws IOException {
    byte[] rawBytes = responseResponseInputStream.readAllBytes();
    return switch (responseResponseInputStream.response().contentType()) {
      case "text/plain" ->
          new DownloadResponse(
              bucket, key, new Element.StringContent(new String(rawBytes, StandardCharsets.UTF_8)));
      case "application/json" ->
          new DownloadResponse(
              bucket, key, new Element.JsonContent(new ObjectMapper().readTree(rawBytes)));
      default ->
          new DownloadResponse(
              bucket, key, new Element.StringContent(Base64.getEncoder().encodeToString(rawBytes)));
    };
  }

  private DeleteResponse delete(DeleteObject deleteObject) {
    DeleteObjectRequest deleteObjectRequest =
        DeleteObjectRequest.builder().bucket(deleteObject.bucket()).key(deleteObject.key()).build();

    this.s3Client.deleteObject(deleteObjectRequest);
    return new DeleteResponse(deleteObject.bucket(), deleteObject.key());
  }
}
