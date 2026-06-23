/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.box;

import static io.camunda.connector.box.BoxUtil.download;
import static io.camunda.connector.box.BoxUtil.getFile;
import static io.camunda.connector.box.BoxUtil.getFolder;
import static io.camunda.connector.box.BoxUtil.item;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxCCGAPIConnection;
import com.box.sdk.BoxConfig;
import com.box.sdk.BoxDeveloperEditionAPIConnection;
import com.box.sdk.BoxFile;
import com.box.sdk.BoxFolder;
import com.box.sdk.BoxItem;
import com.box.sdk.BoxSearch;
import com.box.sdk.BoxSearchParameters;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentReturn;
import io.camunda.connector.api.document.DocumentReturnChoice;
import io.camunda.connector.api.document.RawPayload;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.box.model.BoxRequest;
import io.camunda.connector.box.model.BoxRequest.Operation.Search.SortDirection;
import io.camunda.connector.box.model.BoxResult;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.stream.Collectors;

public class BoxOperations {

  public static Object execute(
      BoxRequest request, OutboundConnectorContext context, boolean useDocumentReturnFlow) {
    var api = connectToApi(request.authentication());
    return switch (request.operation()) {
      case BoxRequest.Operation.UploadFile uploadFile -> uploadFile(uploadFile, api);
      case BoxRequest.Operation.DownloadFile downloadFile ->
          downloadFile(downloadFile, api, context, useDocumentReturnFlow);
      case BoxRequest.Operation.MoveFile moveFile -> moveFile(moveFile, api);
      case BoxRequest.Operation.DeleteFile deleteFile -> deleteFile(deleteFile, api);
      case BoxRequest.Operation.CreateFolder createFolder -> createFolder(createFolder, api);
      case BoxRequest.Operation.DeleteFolder deleteFolder -> deleteFolder(deleteFolder, api);
      case BoxRequest.Operation.Search search -> search(search, api);
    };
  }

  private static BoxAPIConnection connectToApi(BoxRequest.Authentication authentication) {
    return switch (authentication) {
      case BoxRequest.Authentication.DeveloperToken developerToken ->
          new BoxAPIConnection(developerToken.accessToken());

      case BoxRequest.Authentication.ClientCredentialsUser user ->
          BoxCCGAPIConnection.userConnection(user.clientId(), user.clientSecret(), user.userId());

      case BoxRequest.Authentication.ClientCredentialsEnterprise enterprise ->
          BoxCCGAPIConnection.applicationServiceAccountConnection(
              enterprise.clientId(), enterprise.clientSecret(), enterprise.enterpriseId());

      case BoxRequest.Authentication.JWTJsonConfig jwtJsonConfig -> {
        BoxConfig boxConfig = BoxConfig.readFrom(jwtJsonConfig.jsonConfig());
        yield BoxDeveloperEditionAPIConnection.getAppEnterpriseConnection(boxConfig);
      }
    };
  }

  private static BoxResult.Upload uploadFile(
      BoxRequest.Operation.UploadFile uploadFile, BoxAPIConnection api) {
    var folder = getFolder(uploadFile.folderPath(), api);
    var file = folder.uploadFile(uploadFile.document().asInputStream(), uploadFile.getFileName());
    return new BoxResult.Upload(item(file));
  }

  private static Object downloadFile(
      BoxRequest.Operation.DownloadFile downloadFile,
      BoxAPIConnection api,
      OutboundConnectorContext context,
      boolean useDocumentReturnFlow) {
    var file = getFile(downloadFile.filePath(), api);
    if (useDocumentReturnFlow) {
      return newDownloadPath(file);
    }
    var document = createDocument(file, context);
    return new BoxResult.Download(item(file), document);
  }

  private static DocumentReturn<BoxResult> newDownloadPath(BoxFile file) {
    BoxResult.Item itemSnapshot = item(file);
    String fileName = file.getInfo().getName();
    byte[] bytes = download(file);
    RawPayload payload = new RawPayload(new ByteArrayInputStream(bytes), null, fileName);
    return DocumentReturn.of(payload, (converted, choice) -> wrap(itemSnapshot, choice, converted));
  }

  private static BoxResult wrap(
      BoxResult.Item itemSnapshot, DocumentReturnChoice choice, Object converted) {
    return switch (choice) {
      case DOCUMENT -> new BoxResult.Download(itemSnapshot, (Document) converted);
      case TEXT -> new BoxResult.DownloadAsText(itemSnapshot, (String) converted);
      case JSON -> new BoxResult.DownloadAsJson(itemSnapshot, converted);
    };
  }

  private static Document createDocument(BoxFile file, OutboundConnectorContext context) {
    var fileContent = download(file);
    var documentCreationRequest =
        DocumentCreationRequest.from(fileContent).fileName(file.getInfo().getName()).build();
    return context.create(documentCreationRequest);
  }

  private static BoxResult deleteFile(
      BoxRequest.Operation.DeleteFile deleteFile, BoxAPIConnection api) {
    BoxFile file = getFile(deleteFile.filePath(), api);
    file.delete();
    return new BoxResult.Generic(item(file));
  }

  private static BoxResult moveFile(BoxRequest.Operation.MoveFile moveFile, BoxAPIConnection api) {
    BoxFile file = getFile(moveFile.filePath(), api);
    BoxFolder folder = getFolder(moveFile.folderPath(), api);
    BoxItem.Info info = file.move(folder);
    return new BoxResult.Generic(item(info));
  }

  private static BoxResult deleteFolder(
      BoxRequest.Operation.DeleteFolder deleteFolder, BoxAPIConnection api) {
    var folder = getFolder(deleteFolder.folderPath(), api);
    folder.delete(deleteFolder.recursive());
    return new BoxResult.Generic(item(folder));
  }

  private static BoxResult createFolder(
      BoxRequest.Operation.CreateFolder createFolder, BoxAPIConnection api) {
    var folder = getFolder(createFolder.folderPath(), api).createFolder(createFolder.name());
    return new BoxResult.Generic(item(folder));
  }

  private static BoxResult.Search search(BoxRequest.Operation.Search search, BoxAPIConnection api) {
    var searchParams = searchParameters(search);
    var offset = Optional.ofNullable(search.offset()).orElse(0L);
    var limit = Optional.ofNullable(search.limit()).orElse(50L);
    BoxSearch boxSearch = new BoxSearch(api);
    var items =
        boxSearch.searchRange(offset, limit, searchParams).stream()
            .map(BoxUtil::item)
            .collect(Collectors.toList());
    return new BoxResult.Search(items);
  }

  private static BoxSearchParameters searchParameters(BoxRequest.Operation.Search search) {
    BoxSearchParameters searchParams = new BoxSearchParameters();
    searchParams.setQuery(search.query());
    Optional.ofNullable(search.sortColumn()).ifPresent(searchParams::setSort);
    Optional.ofNullable(search.sortDirection())
        .map(SortDirection::getValue)
        .ifPresent(searchParams::setDirection);
    return searchParams;
  }
}
