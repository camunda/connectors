/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.azure.blobstorage.model.core;

import com.azure.core.http.rest.Response;
import com.azure.core.util.BinaryData;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobDownloadContentResponse;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlockBlobItem;
import com.azure.storage.blob.models.DownloadRetryOptions;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import io.camunda.connector.azure.blobstorage.model.request.Authentication;
import io.camunda.connector.azure.blobstorage.model.request.BlobStorageOperation;
import io.camunda.connector.azure.blobstorage.model.request.BlobStorageRequest;
import io.camunda.connector.azure.blobstorage.model.request.DownloadBlob;
import io.camunda.connector.azure.blobstorage.model.request.UploadBlob;
import io.camunda.connector.azure.blobstorage.model.response.DownloadResponse;
import io.camunda.connector.azure.blobstorage.model.response.DownloadResponse.DocumentContent;
import io.camunda.connector.azure.blobstorage.model.response.UploadResponse;
import io.camunda.document.Document;
import io.camunda.document.store.DocumentCreationRequest;
import java.time.Duration;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlobStorageExecutor {
  private static final Logger log = LoggerFactory.getLogger(BlobStorageExecutor.class);

  private final Function<DocumentCreationRequest, Document> createDocument;
  private final Authentication authentication;

  public BlobStorageExecutor(
      Authentication authentication, Function<DocumentCreationRequest, Document> createDocument) {
    this.authentication = authentication;
    this.createDocument = createDocument;
  }

  public static BlobStorageExecutor create(
      BlobStorageRequest blobStorageRequest,
      Function<DocumentCreationRequest, Document> createDocument) {
    return new BlobStorageExecutor(blobStorageRequest.getAuthentication(), createDocument);
  }

  public Object execute(BlobStorageOperation blobStorageOperation) {
    return switch (blobStorageOperation) {
      case DownloadBlob downloadBlob -> download(downloadBlob);
      case UploadBlob uploadBlob -> upload(uploadBlob);
    };
  }

  private UploadResponse upload(UploadBlob uploadBlob) {
    BlobContainerClient blobContainerClient =
        new BlobContainerClientBuilder()
            .endpoint(this.authentication.getSASUrl())
            .sasToken(this.authentication.getSASToken())
            .containerName(uploadBlob.container())
            .buildClient();

    String fileName =
        uploadBlob.fileName() != null && !uploadBlob.fileName().isEmpty()
            ? uploadBlob.fileName()
            : uploadBlob.document().metadata().getFileName();

    BlobClient blobClient = blobContainerClient.getBlobClient(fileName);

    BlobHttpHeaders headers =
        new BlobHttpHeaders().setContentType(uploadBlob.document().metadata().getContentType());
    BlobParallelUploadOptions options =
        new BlobParallelUploadOptions(uploadBlob.document().asInputStream()).setHeaders(headers);

    Response<BlockBlobItem> response =
        blobClient.uploadWithResponse(
            options, Duration.ofSeconds(uploadBlob.timeout()), Context.NONE);

    log.debug(
        "Upload of file {} to container {} finished with status code: {}",
        fileName,
        blobClient.getContainerName(),
        response.getStatusCode());
    return new UploadResponse(
        blobClient.getContainerName(), blobClient.getBlobName(), blobClient.getBlobUrl());
  }

  private DownloadResponse download(DownloadBlob downloadBlob) {
    BlobContainerClient blobContainerClient =
        new BlobContainerClientBuilder()
            .endpoint(this.authentication.getSASUrl())
            .sasToken(this.authentication.getSASToken())
            .containerName(downloadBlob.container())
            .buildClient();
    DownloadRetryOptions options = new DownloadRetryOptions().setMaxRetryRequests(3);

    BlobClient blobClient = blobContainerClient.getBlobClient(downloadBlob.fileName());
    BlobDownloadContentResponse contentResponse =
        blobClient.downloadContentWithResponse(options, null, null, null);

    BinaryData content = contentResponse.getValue();

    log.debug(
        "Download of file {} from container {} finished with status code: {}",
        blobClient.getBlobName(),
        blobClient.getContainerName(),
        contentResponse.getStatusCode());

    if (downloadBlob.asFile()) {
      return this.createDocument
          .andThen(DocumentContent::new)
          .apply(
              DocumentCreationRequest.from(content.toBytes())
                  .contentType(contentResponse.getDeserializedHeaders().getContentType())
                  .fileName(downloadBlob.fileName())
                  .build());
    } else {
      return new DownloadResponse.StringContent(content.toString());
    }
  }
}
