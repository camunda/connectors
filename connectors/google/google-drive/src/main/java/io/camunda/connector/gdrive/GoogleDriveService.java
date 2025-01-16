/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive;

import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.json.JsonParser;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.docs.v1.model.BatchUpdateDocumentResponse;
import com.google.api.services.docs.v1.model.Request;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.common.reflect.TypeToken;
import io.camunda.connector.gdrive.model.GoogleDriveResult;
import io.camunda.connector.gdrive.model.MimeTypeUrl;
import io.camunda.connector.gdrive.model.request.Resource;
import io.camunda.connector.gdrive.model.request.Template;
import io.camunda.connector.gdrive.model.request.Type;
import io.camunda.connector.gdrive.model.request.Variables;
import io.camunda.document.Document;
import io.camunda.google.supplier.GsonComponentSupplier;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleDriveService {

  public static final int MAX_DIRECT_UPLOAD_FILE_SIZE = 5242880; // 5MB
  private static final Logger LOGGER = LoggerFactory.getLogger(GoogleDriveService.class);

  private final GsonFactory gsonFactory = GsonComponentSupplier.gsonFactoryInstance();

  public GoogleDriveService() {}

  public Object execute(final GoogleDriveClient client, final Resource resource) {
    return switch (resource.type()) {
      case FOLDER -> createFolder(client, resource);
      case FILE -> createFile(client, resource);
      case UPLOAD -> uploadFile(client, resource);
      case DOWNLOAD -> downloadFile(client, resource);
    };
  }

  private GoogleDriveResult createFolder(final GoogleDriveClient client, final Resource resource) {
    File fileMetadata = createMetaDataFile(resource);
    File result = client.createWithMetadata(fileMetadata);
    LOGGER.debug(
        "Folder successfully created, id: [{}] name: [{}]", result.getId(), resource.name());
    return new GoogleDriveResult(
        result.getId(), MimeTypeUrl.getResourceUrl(result.getMimeType(), result.getId()));
  }

  private GoogleDriveResult createFile(final GoogleDriveClient client, final Resource resource) {
    final File metaData = createMetaDataFile(resource);
    File result;
    if (resource.template() != null) {
      result = client.createWithTemplate(metaData, resource.template().id());
      LOGGER.debug(
          "File successfully created by template, file name [{}], templateId [{}]",
          result.getId(),
          resource.name());
      Optional.of(resource)
          .map(Resource::template)
          .map(Template::variables)
          .map(Variables::requests)
          .map(Object::toString)
          .map(this::mapJsonToRequests)
          .ifPresent(requests -> updateWithRequests(client, requests, result));
    } else {
      result = client.createWithMetadata(metaData);
    }
    return new GoogleDriveResult(
        result.getId(), MimeTypeUrl.getResourceUrl(result.getMimeType(), result.getId()));
  }

  private List<Request> mapJsonToRequests(String requestsAsJson) {
    try {
      JsonParser jsonParser = gsonFactory.createJsonParser(requestsAsJson);
      java.lang.reflect.Type listType = new TypeToken<ArrayList<Request>>() {}.getType();
      return (List<Request>) jsonParser.parse(listType, true);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private File createMetaDataFile(final Resource resource) {
    File file;
    if (resource.additionalGoogleDriveProperties() == null) {
      file = new File();
    } else {
      file = mapJsonToFile(resource.additionalGoogleDriveProperties().toString());
    }
    return file.setName(resource.name())
        .setMimeType(resource.type() == Type.FOLDER ? MimeTypeUrl.FOLDER.getMimeType() : null)
        .setParents(resource.parent() != null ? List.of(resource.parent()) : null);
  }

  private File mapJsonToFile(String json) {
    try {
      JsonParser jsonParser = gsonFactory.createJsonParser(json);
      return jsonParser.parseAndClose(File.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void updateWithRequests(
      final GoogleDriveClient client, final List<Request> requests, final File metaData) {
    if (MimeTypeUrl.DOCUMENT.getMimeType().equals(metaData.getMimeType())) {
      BatchUpdateDocumentResponse response = client.updateDocument(metaData.getId(), requests);
      LOGGER.debug("Variables successfully replaced for file id [{}]", response.getDocumentId());
    } else {
      LOGGER.warn(
          "It was not possible to update file id [{}] "
              + "this feature is not yet implemented for the type [{}]",
          metaData.getId(),
          metaData.getMimeType());
    }
  }

  private GoogleDriveResult uploadFile(final GoogleDriveClient client, final Resource resource) {
    try {
      var document = resource.uploadData().document();
      File fileMetaData = prepareFileMetaData(document, resource.parent());

      var content =
          new ByteArrayContent(document.metadata().getContentType(), document.asByteArray());

      Drive drive = client.getDriveService();
      Drive.Files.Create createRequest = drive.files().create(fileMetaData, content);

      if (document.metadata().getSize() > MAX_DIRECT_UPLOAD_FILE_SIZE) {
        createRequest.getMediaHttpUploader().setProgressListener(new CustomProgressListener());
      }

      File file = createRequest.execute();
      return new GoogleDriveResult(file.getId(), MimeTypeUrl.getFileUrl(file.getId()));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private Pair<File, ByteArrayOutputStream> downloadFile(
      final GoogleDriveClient client, final Resource resource) {
    Drive drive = client.getDriveService();
    try {
      String id = resource.downloadData().id();
      File fileMetaData = drive.files().get(id).execute();
      var outputStream = new ByteArrayOutputStream();

      drive.files().get(id).executeMediaAndDownloadTo(outputStream);

      return Pair.of(fileMetaData, outputStream);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private File prepareFileMetaData(Document document, String parent) {
    File fileMetaData = new File();
    fileMetaData.setName(document.metadata().getFileName());

    Optional.ofNullable(parent)
        .filter(StringUtils::isNoneBlank)
        .ifPresent(folder -> fileMetaData.setParents(List.of(folder)));

    return fileMetaData;
  }
}
