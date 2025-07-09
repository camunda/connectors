/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.google.gcs.model.core;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.google.gcs.model.request.Authentication;
import io.camunda.connector.google.gcs.model.request.DownloadObject;
import io.camunda.connector.google.gcs.model.request.ObjectStorageOperation;
import io.camunda.connector.google.gcs.model.request.ObjectStorageRequest;
import io.camunda.connector.google.gcs.model.request.UploadObject;
import io.camunda.connector.google.gcs.model.response.DownloadResponse;
import io.camunda.connector.google.gcs.model.response.DownloadResponse.DocumentContent;
import io.camunda.connector.google.gcs.model.response.UploadResponse;
import io.camunda.document.Document;
import io.camunda.document.store.DocumentCreationRequest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ObjectStorageExecutor {

  private static final Logger log = LoggerFactory.getLogger(ObjectStorageExecutor.class);

  private final Function<DocumentCreationRequest, Document> createDocument;
  private final Authentication authentication;

  public ObjectStorageExecutor(
      Authentication authentication, Function<DocumentCreationRequest, Document> createDocument) {
    this.authentication = authentication;
    this.createDocument = createDocument;
  }

  public static ObjectStorageExecutor create(
      ObjectStorageRequest objectStorageRequest,
      Function<DocumentCreationRequest, Document> createDocument) {
    return new ObjectStorageExecutor(objectStorageRequest.getAuthentication(), createDocument);
  }

  public Object execute(ObjectStorageOperation objectStorageOperation) {
    return switch (objectStorageOperation) {
      case DownloadObject downloadObject -> download(downloadObject);
      case UploadObject uploadObject -> upload(uploadObject);
    };
  }

  private UploadResponse upload(UploadObject uploadObject) {
    String fileName =
        uploadObject.fileName() != null && !uploadObject.fileName().isEmpty()
            ? uploadObject.fileName()
            : uploadObject.document().metadata().getFileName();
    BlobId blobId = BlobId.of(uploadObject.bucket(), fileName);
    BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();

    try {
      ServiceAccountCredentials sac =
          ServiceAccountCredentials.fromStream(
              new ByteArrayInputStream(
                  authentication.getJsonKey().getBytes(StandardCharsets.UTF_8)));
      Storage storage =
          StorageOptions.newBuilder()
              .setProjectId(uploadObject.project())
              .setCredentials(sac)
              .build()
              .getService();

      Blob blob = storage.createFrom(blobInfo, uploadObject.document().asInputStream());
      return new UploadResponse(blob.getBucket(), blob.getName());
    } catch (IOException ioException) {
      log.error(
          "GCS: Failed to upload file {} to bucket {}",
          fileName,
          uploadObject.bucket(),
          ioException);
      throw new ConnectorException("Failed to upload file", ioException);
    }
  }

  private DownloadResponse download(DownloadObject downloadObject) {
    StorageOptions storageOptions =
        StorageOptions.newBuilder().setProjectId(downloadObject.project()).build();
    try (Storage storage = storageOptions.getService()) {
      BlobId blobId = BlobId.of(downloadObject.bucket(), downloadObject.fileName());
      if (downloadObject.asDocument()) {
        return downloadAsDocument(storage, blobId, downloadObject.fileName());
      } else {
        return downloadAsString(storage, blobId);
      }
    } catch (Exception e) {
      log.error(
          "GCS: Failed to download file {} from bucket {}",
          downloadObject.fileName(),
          downloadObject.bucket(),
          e);
      throw new ConnectorException(e);
    }
  }

  private DownloadResponse downloadAsDocument(Storage storage, BlobId blobId, String filename) {
    try (ReadChannel reader = storage.reader(blobId)) {
      Blob blob = storage.get(blobId);
      Optional<String> contentTypeOpt = Optional.ofNullable(blob).map(Blob::getContentType);
      InputStream inputStream = Channels.newInputStream(reader);
      DocumentCreationRequest.BuilderFinalStep requestBuilder =
          DocumentCreationRequest.from(inputStream).fileName(filename);
      contentTypeOpt.ifPresent(requestBuilder::contentType);
      return this.createDocument.andThen(DocumentContent::new).apply(requestBuilder.build());
    }
  }

  private DownloadResponse downloadAsString(Storage storage, BlobId blobId) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    storage.downloadTo(blobId, outputStream);
    return new DownloadResponse.StringContent(outputStream.toString(StandardCharsets.UTF_8));
  }
}
