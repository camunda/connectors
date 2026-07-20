/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.box.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.generator.java.annotation.DocumentReturnFormat;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateDocumentProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BoxRequest(
    @TemplateProperty(group = "authentication", id = "authentication") @Valid @NotNull
        Authentication authentication,
    @TemplateProperty(group = "operation", id = "operation") @Valid @NotNull Operation operation) {

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = Authentication.DeveloperToken.class, name = "developerToken"),
    @JsonSubTypes.Type(
        value = Authentication.ClientCredentialsUser.class,
        name = "clientCredentialsUser"),
    @JsonSubTypes.Type(
        value = Authentication.ClientCredentialsEnterprise.class,
        name = "clientCredentialsEnterprise"),
    @JsonSubTypes.Type(value = Authentication.JWTJsonConfig.class, name = "jwtJsonConfig")
  })
  @TemplateDiscriminatorProperty(
      label = "Authentication",
      group = "authentication",
      name = "type",
      defaultValue = "developerToken",
      description =
          "Specify authentication strategy. Learn more at the <a href=\"https://developer.box.com/guides/authentication/\" target=\"_blank\">documentation page</a>")
  public sealed interface Authentication
      permits Authentication.ClientCredentialsEnterprise,
          Authentication.ClientCredentialsUser,
          Authentication.DeveloperToken,
          Authentication.JWTJsonConfig {
    @TemplateSubType(id = "developerToken", label = "Developer token")
    record DeveloperToken(
        @TemplateProperty(
                group = "authentication",
                label = "Access key",
                tooltip = "The access key or developer token")
            @NotBlank
            String accessToken)
        implements Authentication {}

    @TemplateSubType(id = "clientCredentialsUser", label = "Client Credentials User")
    record ClientCredentialsUser(
        @TemplateProperty(group = "authentication", id = "clientIdUser", label = "Client id")
            @NotBlank
            String clientId,
        @TemplateProperty(
                group = "authentication",
                id = "clientSecretUser",
                label = "Client secret")
            @NotBlank
            String clientSecret,
        @TemplateProperty(
                group = "authentication",
                label = "User ID",
                tooltip = "The user ID of the account to authenticate against")
            @NotBlank
            String userId)
        implements Authentication {}

    @TemplateSubType(id = "clientCredentialsEnterprise", label = "Client Credentials Enterprise")
    record ClientCredentialsEnterprise(
        @TemplateProperty(group = "authentication", id = "clientIdEnterprise", label = "Client id")
            @NotBlank
            String clientId,
        @TemplateProperty(
                group = "authentication",
                id = "clientSecretEnterprise",
                label = "Client secret")
            @NotBlank
            String clientSecret,
        @TemplateProperty(
                group = "authentication",
                label = "Enterprise ID",
                tooltip = "The enterprise ID to authenticate against")
            @NotBlank
            String enterpriseId)
        implements Authentication {}

    @TemplateSubType(id = "jwtJsonConfig", label = "JWT JSON Config")
    record JWTJsonConfig(
        @TemplateProperty(
                group = "authentication",
                label = "JSON config",
                tooltip = "The JSON config as string")
            @NotBlank
            String jsonConfig)
        implements Authentication {}
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = Operation.CreateFolder.class, name = "createFolder"),
    @JsonSubTypes.Type(value = Operation.DeleteFolder.class, name = "deleteFolder"),
    @JsonSubTypes.Type(value = Operation.UploadFile.class, name = "uploadFile"),
    @JsonSubTypes.Type(value = Operation.DownloadFile.class, name = "downloadFile"),
    @JsonSubTypes.Type(value = Operation.MoveFile.class, name = "moveFile"),
    @JsonSubTypes.Type(value = Operation.DeleteFile.class, name = "deleteFile"),
    @JsonSubTypes.Type(value = Operation.Search.class, name = "search")
  })
  @TemplateDiscriminatorProperty(
      label = "Operation",
      group = "operation",
      name = "type",
      defaultValue = "createFolder",
      description = "The operation to execute.")
  public sealed interface Operation
      permits Operation.CreateFolder,
          Operation.DeleteFolder,
          Operation.UploadFile,
          Operation.DownloadFile,
          Operation.MoveFile,
          Operation.DeleteFile,
          Operation.Search {
    @TemplateSubType(
        id = "createFolder",
        label = "Create Folder",
        description = "Create a new folder in Box",
        keywords = {"create folder", "new folder", "add directory", "make folder"})
    record CreateFolder(
        @TemplateProperty(id = "createFolderName", group = "operation", label = "Folder name")
            @NotBlank
            String name,
        @TemplateProperty(
                id = "createFolderParentPath",
                group = "operation",
                label = "Parent path",
                defaultValue = "/")
            @NotBlank
            String folderPath)
        implements Operation {}

    @TemplateSubType(
        id = "deleteFolder",
        label = "Delete Folder",
        description = "Delete a folder from Box",
        keywords = {"delete folder", "remove folder", "trash folder", "erase directory"})
    record DeleteFolder(
        @TemplateProperty(id = "deleteFolderPath", group = "operation", label = "Folder path")
            @NotBlank
            String folderPath,
        @TemplateProperty(
                defaultValue = "true",
                defaultValueType = TemplateProperty.DefaultValueType.Boolean,
                group = "operation",
                label = "Recursive",
                tooltip = "Deletes all items contained by the folder")
            boolean recursive)
        implements Operation {}

    @TemplateSubType(
        id = "uploadFile",
        label = "Upload File",
        description = "Upload a file to Box",
        keywords = {"upload file", "store file", "save document", "put file", "publish file"})
    record UploadFile(
        @TemplateProperty(id = "uploadFileName", group = "operation", label = "File name") @NotBlank
            String name,
        @TemplateProperty(id = "uploadFileFolderPath", group = "operation", label = "Folder path")
            @NotBlank
            String folderPath,
        @TemplateDocumentProperty(
                id = "uploadFileDocument",
                group = "operation",
                tooltip = "The document reference that will be uploaded")
            @NotNull
            Document document)
        implements Operation {

      public String getFileName() {
        return name != null ? name : document.metadata().getFileName();
      }
    }

    @TemplateSubType(
        id = "downloadFile",
        label = "Download File",
        description = "Download a file from Box",
        keywords = {"download file", "get file", "fetch file", "retrieve document", "export file"})
    @DocumentReturnFormat(
        group = "operation",
        tooltip =
            "How the downloaded payload should be returned. Document reference uploads the payload"
                + " to the document store; as text decodes it as a String; as JSON parses it into a"
                + " structure you can access via dot notation.")
    record DownloadFile(
        @TemplateProperty(
                id = "downloadFilePath",
                group = "operation",
                label = "File path",
                tooltip = "Path to the file item to download")
            String filePath)
        implements Operation {}

    @TemplateSubType(
        id = "moveFile",
        label = "Move File",
        description = "Move a file to a different folder in Box",
        keywords = {"move file", "relocate file", "transfer file", "cut file"})
    record MoveFile(
        @TemplateProperty(id = "moveFilePath", group = "operation", label = "File path") @NotBlank
            String filePath,
        @TemplateProperty(
                id = "moveFileFolderPath",
                group = "operation",
                label = "Target folder path")
            @NotBlank
            String folderPath)
        implements Operation {}

    @TemplateSubType(
        id = "deleteFile",
        label = "Delete File",
        description = "Delete a file from Box",
        keywords = {"delete file", "remove file", "erase file", "trash file"})
    record DeleteFile(
        @TemplateProperty(id = "deleteFilePath", group = "operation", label = "File path") @NotBlank
            String filePath)
        implements Operation {}

    @TemplateSubType(
        id = "search",
        label = "Search",
        description = "Search for files and folders in Box",
        keywords = {"search", "find files", "look up content", "query files", "locate documents"})
    record Search(
        @TemplateProperty(id = "searchQuery", group = "operation") @NotBlank String query,
        @TemplateProperty(
                id = "searchSortColumn",
                defaultValue = "modified_at",
                group = "operation")
            @NotBlank
            String sortColumn,
        @TemplateProperty(
                id = "searchSortDirection",
                defaultValue = "DESC",
                tooltip = "Sort order for results: ASC (ascending) or DESC (descending)",
                group = "operation")
            SortDirection sortDirection,
        @TemplateProperty(
                id = "searchOffset",
                defaultValueType = TemplateProperty.DefaultValueType.Number,
                defaultValue = "0",
                group = "operation")
            Long offset,
        @TemplateProperty(
                id = "searchLimit",
                defaultValueType = TemplateProperty.DefaultValueType.Number,
                defaultValue = "30",
                group = "operation")
            @Min(1)
            @Max(200)
            Long limit)
        implements Operation {

      public enum SortDirection {
        ASC("ASC"),
        DESC("DESC");

        private final String value;

        SortDirection(String value) {
          this.value = value;
        }

        public String getValue() {
          return value;
        }
      }
    }
  }
}
