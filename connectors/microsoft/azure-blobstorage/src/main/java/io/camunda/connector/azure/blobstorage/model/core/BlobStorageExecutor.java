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
import io.camunda.connector.azure.blobstorage.model.request.BlobStorageAction;
import io.camunda.connector.azure.blobstorage.model.request.BlobStorageRequest;
import io.camunda.connector.azure.blobstorage.model.request.DownloadObject;
import io.camunda.connector.azure.blobstorage.model.request.UploadObject;
import io.camunda.connector.azure.blobstorage.model.response.DownloadResponse;
import io.camunda.connector.azure.blobstorage.model.response.Element;
import io.camunda.connector.azure.blobstorage.model.response.UploadResponse;
import io.camunda.document.Document;
import io.camunda.document.store.DocumentCreationRequest;
import java.time.Duration;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlobStorageExecutor {
  private final long UPLOAD_TIMEOUT_IN_SECONDS = 30;

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

  public Object execute(BlobStorageAction blobStorageAction) {
    return switch (blobStorageAction) {
      case DownloadObject downloadObject -> download(downloadObject);
      case UploadObject uploadObject -> upload(uploadObject);
    };
  }

  private UploadResponse upload(UploadObject uploadObject) {
    BlobContainerClient blobContainerClient =
        new BlobContainerClientBuilder()
            .endpoint(this.authentication.getSASUrl())
            .sasToken(this.authentication.getSAStoken())
            .containerName(uploadObject.container())
            .buildClient();

    String fileName =
        uploadObject.fileName() != null && !uploadObject.fileName().isEmpty()
            ? uploadObject.fileName()
            : uploadObject.document().metadata().getFileName();

    BlobClient blobClient = blobContainerClient.getBlobClient(fileName);

    BlobHttpHeaders headers =
        new BlobHttpHeaders().setContentType(uploadObject.document().metadata().getContentType());
    BlobParallelUploadOptions options =
        new BlobParallelUploadOptions(uploadObject.document().asInputStream()).setHeaders(headers);

    Response<BlockBlobItem> response =
        blobClient.uploadWithResponse(
            options, Duration.ofSeconds(UPLOAD_TIMEOUT_IN_SECONDS), Context.NONE);

    log.debug(
        "Upload of file {} to container {} finished with status code: {}",
        fileName,
        blobClient.getContainerName(),
        response.getStatusCode());
    return new UploadResponse(
        blobClient.getContainerName(), blobClient.getBlobName(), blobClient.getBlobUrl());
  }

  private DownloadResponse download(DownloadObject downloadObject) {
    BlobContainerClient blobContainerClient =
        new BlobContainerClientBuilder()
            .endpoint(this.authentication.getSASUrl())
            .sasToken(this.authentication.getSAStoken())
            .containerName(downloadObject.container())
            .buildClient();
    DownloadRetryOptions options = new DownloadRetryOptions().setMaxRetryRequests(3);

    BlobClient blobClient = blobContainerClient.getBlobClient(downloadObject.fileName());
    BlobDownloadContentResponse contentResponse =
        blobClient.downloadContentWithResponse(options, null, null, null);

    BinaryData content = contentResponse.getValue();

    log.debug(
        "Download of file {} to container {} finished with status code: {}",
        blobClient.getBlobName(),
        blobClient.getContainerName(),
        contentResponse.getStatusCode());

    if (downloadObject.asFile()) {
      return this.createDocument
          .andThen(document -> new DownloadResponse(new Element.DocumentContent(document)))
          .apply(
              DocumentCreationRequest.from(content.toBytes())
                  .contentType(contentResponse.getDeserializedHeaders().getContentType())
                  .fileName(downloadObject.fileName())
                  .build());
    } else {
      return new DownloadResponse(new Element.StringContent(content.toString()));
    }
  }
}
