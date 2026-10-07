/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.box;

import static io.camunda.connector.box.BoxUtil.download;
import static io.camunda.connector.box.BoxUtil.getItemId;

import com.box.sdkgen.box.ccgauth.BoxCCGAuth;
import com.box.sdkgen.box.ccgauth.CCGConfig;
import com.box.sdkgen.box.developertokenauth.BoxDeveloperTokenAuth;
import com.box.sdkgen.box.jwtauth.BoxJWTAuth;
import com.box.sdkgen.box.jwtauth.JWTConfig;
import com.box.sdkgen.client.BoxClient;
import com.box.sdkgen.managers.files.UpdateFileByIdRequestBody;
import com.box.sdkgen.managers.files.UpdateFileByIdRequestBodyParentField;
import com.box.sdkgen.managers.folders.CreateFolderRequestBody;
import com.box.sdkgen.managers.folders.CreateFolderRequestBodyParentField;
import com.box.sdkgen.managers.folders.DeleteFolderByIdQueryParams;
import com.box.sdkgen.managers.search.SearchForContentQueryParams;
import com.box.sdkgen.managers.search.SearchForContentQueryParamsDirectionField;
import com.box.sdkgen.managers.search.SearchForContentQueryParamsSortField;
import com.box.sdkgen.managers.uploads.UploadFileRequestBody;
import com.box.sdkgen.managers.uploads.UploadFileRequestBodyAttributesField;
import com.box.sdkgen.managers.uploads.UploadFileRequestBodyAttributesParentField;
import com.box.sdkgen.networking.auth.Authentication;
import com.box.sdkgen.networking.network.NetworkSession;
import com.box.sdkgen.schemas.filefull.FileFull;
import com.box.sdkgen.schemas.folderfull.FolderFull;
import com.box.sdkgen.serialization.json.EnumWrapper;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentReturn;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.box.model.BoxRequest;
import io.camunda.connector.box.model.BoxResult;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.stream.Collectors;

public class BoxOperations {

  public static Object execute(
      BoxRequest request, OutboundConnectorContext context, boolean useDocumentReturnFlow) {
    try {
      return run(request, context, useDocumentReturnFlow);
    } catch (RuntimeException e) {
      throw BoxErrors.translate(e);
    }
  }

  private static Object run(
      BoxRequest request, OutboundConnectorContext context, boolean useDocumentReturnFlow) {
    var client = connectToApi(request.authentication());
    return switch (request.operation()) {
      case BoxRequest.Operation.UploadFile uploadFile -> uploadFile(uploadFile, client);
      case BoxRequest.Operation.DownloadFile downloadFile ->
          downloadFile(downloadFile, client, context, useDocumentReturnFlow);
      case BoxRequest.Operation.MoveFile moveFile -> moveFile(moveFile, client);
      case BoxRequest.Operation.DeleteFile deleteFile -> deleteFile(deleteFile, client);
      case BoxRequest.Operation.CreateFolder createFolder -> createFolder(createFolder, client);
      case BoxRequest.Operation.DeleteFolder deleteFolder -> deleteFolder(deleteFolder, client);
      case BoxRequest.Operation.Search search -> search(search, client);
    };
  }

  private static BoxClient connectToApi(BoxRequest.Authentication authentication) {
    return connectToApi(authentication, NETWORK_SESSION);
  }

  static BoxClient connectToApi(
      BoxRequest.Authentication authentication, NetworkSession networkSession) {
    return switch (authentication) {
      case BoxRequest.Authentication.DeveloperToken developerToken ->
          client(new BoxDeveloperTokenAuth(developerToken.accessToken()), networkSession);

      case BoxRequest.Authentication.ClientCredentialsUser user ->
          client(
              new BoxCCGAuth(new CCGConfig(user.clientId(), user.clientSecret()))
                  .withUserSubject(user.userId()),
              networkSession);

      case BoxRequest.Authentication.ClientCredentialsEnterprise enterprise ->
          client(
              new BoxCCGAuth(new CCGConfig(enterprise.clientId(), enterprise.clientSecret()))
                  .withEnterpriseSubject(enterprise.enterpriseId()),
              networkSession);

      case BoxRequest.Authentication.JWTJsonConfig jwtJsonConfig ->
          client(
              new BoxJWTAuth(JWTConfig.fromConfigJsonString(jwtJsonConfig.jsonConfig())),
              networkSession);
    };
  }

  private static final NetworkSession NETWORK_SESSION =
      new NetworkSession().withRetryStrategy(new BodyAwareRetryStrategy());

  private static BoxClient client(Authentication auth, NetworkSession networkSession) {
    return new BoxClient.Builder(auth).networkSession(networkSession).build();
  }

  static BoxResult.Upload uploadFile(BoxRequest.Operation.UploadFile uploadFile, BoxClient client) {
    var folderId = getItemId(uploadFile.folderPath(), client);
    InputStream content = new SingleUseInputStream(uploadFile.document().asInputStream());
    try {
      var requestBody =
          new UploadFileRequestBody(
              new UploadFileRequestBodyAttributesField(
                  uploadFile.getFileName(),
                  new UploadFileRequestBodyAttributesParentField(folderId)),
              content);
      FileFull file = client.uploads.uploadFile(requestBody).getEntries().get(0);
      return new BoxResult.Upload(new BoxResult.Item(file.getId(), "file"));
    } finally {
      closeQuietly(content);
    }
  }

  private static void closeQuietly(InputStream stream) {
    try {
      stream.close();
    } catch (IOException ignored) {
      // the upload outcome is what matters; a failing close must not mask it
    }
  }

  private static Object downloadFile(
      BoxRequest.Operation.DownloadFile downloadFile,
      BoxClient client,
      OutboundConnectorContext context,
      boolean useDocumentReturnFlow) {
    var fileId = getItemId(downloadFile.filePath(), client);
    if (useDocumentReturnFlow) {
      return newDownloadPath(fileId, client);
    } else {
      var document = createDocument(fileId, client, context);
      return new BoxResult.Download(new BoxResult.Item(fileId, "file"), document);
    }
  }

  private static DocumentReturn<BoxResult> newDownloadPath(String fileId, BoxClient client) {
    BoxResult.Item itemSnapshot = new BoxResult.Item(fileId, "file");
    String fileName = client.files.getFileById(fileId).getName();
    byte[] bytes = download(fileId, client);
    return DocumentReturn.of(
        bytes,
        null,
        fileName,
        (converted, choice) -> BoxResult.forDownload(itemSnapshot, choice, converted));
  }

  private static Document createDocument(
      String fileId, BoxClient client, OutboundConnectorContext context) {
    byte[] fileContent = download(fileId, client);
    String fileName = client.files.getFileById(fileId).getName();
    var documentCreationRequest =
        DocumentCreationRequest.from(fileContent).fileName(fileName).build();
    return context.create(documentCreationRequest);
  }

  private static BoxResult deleteFile(
      BoxRequest.Operation.DeleteFile deleteFile, BoxClient client) {
    var fileId = getItemId(deleteFile.filePath(), client);
    client.files.deleteFileById(fileId);
    return new BoxResult.Generic(new BoxResult.Item(fileId, "file"));
  }

  private static BoxResult moveFile(BoxRequest.Operation.MoveFile moveFile, BoxClient client) {
    var fileId = getItemId(moveFile.filePath(), client);
    var folderId = getItemId(moveFile.folderPath(), client);
    var requestBody =
        new UpdateFileByIdRequestBody.Builder()
            .parent(new UpdateFileByIdRequestBodyParentField.Builder().id(folderId).build())
            .build();
    FileFull file = client.files.updateFileById(fileId, requestBody);
    return new BoxResult.Generic(new BoxResult.Item(file.getId(), "file"));
  }

  private static BoxResult deleteFolder(
      BoxRequest.Operation.DeleteFolder deleteFolder, BoxClient client) {
    var folderId = getItemId(deleteFolder.folderPath(), client);
    var queryParams = new DeleteFolderByIdQueryParams();
    queryParams.recursive = deleteFolder.recursive();
    client.folders.deleteFolderById(folderId, queryParams);
    return new BoxResult.Generic(new BoxResult.Item(folderId, "folder"));
  }

  private static BoxResult createFolder(
      BoxRequest.Operation.CreateFolder createFolder, BoxClient client) {
    var parentId = getItemId(createFolder.folderPath(), client);
    var requestBody =
        new CreateFolderRequestBody(
            createFolder.name(), new CreateFolderRequestBodyParentField(parentId));
    FolderFull folder = client.folders.createFolder(requestBody);
    return new BoxResult.Generic(new BoxResult.Item(folder.getId(), "folder"));
  }

  private static BoxResult.Search search(BoxRequest.Operation.Search search, BoxClient client) {
    var queryParams = new SearchForContentQueryParams();
    queryParams.query = search.query();
    queryParams.offset = Optional.ofNullable(search.offset()).orElse(0L);
    queryParams.limit = Optional.ofNullable(search.limit()).orElse(50L);
    Optional.ofNullable(search.sortColumn())
        .ifPresent(
            sort -> queryParams.sort = new EnumWrapper<SearchForContentQueryParamsSortField>(sort));
    Optional.ofNullable(search.sortDirection())
        .map(BoxRequest.Operation.Search.SortDirection::getValue)
        .ifPresent(
            direction ->
                queryParams.direction =
                    new EnumWrapper<SearchForContentQueryParamsDirectionField>(direction));
    var items =
        client.search.searchForContent(queryParams).getSearchResults().getEntries().stream()
            .map(entry -> new BoxResult.Item(entry.getId(), entry.getType()))
            .collect(Collectors.toList());
    return new BoxResult.Search(items);
  }
}
