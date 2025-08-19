/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.utils;

import io.camunda.connector.api.document.Document;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.textract.model.DocumentLocation;
import software.amazon.awssdk.services.textract.model.S3Object;

public class AwsS3Util {
  private static final Logger LOGGER = LoggerFactory.getLogger(AwsS3Util.class);

  private static String uploadNewFileFromDocument(
      final Document document, final String bucketName, final S3AsyncClient s3AsyncClient)
      throws IOException {
    String documentKey = UUID.randomUUID().toString();
    LOGGER.debug("Starting document upload to AWS S3 with key {}", documentKey);
    long contentLength = document.metadata().getSize();
    try (InputStream inputStream = document.asInputStream()) {
      PutObjectRequest putObjectRequest =
          PutObjectRequest.builder().bucket(bucketName).key(documentKey).build();

      try (ExecutorService executorService = Executors.newSingleThreadExecutor()) {
        AsyncRequestBody asyncRequestBody =
            AsyncRequestBody.fromInputStream(
                body ->
                    body.executor(executorService)
                        .contentLength(contentLength)
                        .inputStream(inputStream)
                        .build());
        s3AsyncClient.putObject(putObjectRequest, asyncRequestBody).join();
      }
    }

    LOGGER.debug("Document with key {} uploaded to AWS S3 successfully", documentKey);
    return documentKey;
  }

  public static void deleteS3ObjectFromBucketAsync(
      final String key, final String bucketName, final S3AsyncClient s3AsyncClient) {
    DeleteObjectRequest deleteObjectRequest =
        DeleteObjectRequest.builder().bucket(bucketName).key(key).build();

    CompletableFuture<DeleteObjectResponse> response =
        s3AsyncClient.deleteObject(deleteObjectRequest);
    response.whenComplete(
        (deleteResult, exception) -> {
          if (deleteResult != null) {
            LOGGER.debug("Document with key {} was deleted successfully", key);
          } else {
            throw new RuntimeException("An S3 exception occurred during delete", exception);
          }
        });

    response.thenApply(r -> null);
  }

  public static S3Object buildS3ObjectFromDocument(
      final Document document, final String bucketName, final S3AsyncClient s3AsyncClient)
      throws IOException {
    return S3Object.builder()
        .bucket(bucketName)
        .name(uploadNewFileFromDocument(document, bucketName, s3AsyncClient))
        .build();
  }

  public static DocumentLocation buildDocumentLocation(final S3Object s3Object) {
    return DocumentLocation.builder().s3Object(s3Object).build();
  }
}
