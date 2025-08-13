/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.operation.channel;

import com.microsoft.graph.core.models.UploadResult;
import com.microsoft.graph.core.tasks.LargeFileUploadTask;
import com.microsoft.graph.drives.item.items.item.createuploadsession.CreateUploadSessionPostRequestBody;
import com.microsoft.graph.models.ChatMessageAttachment;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.DriveItemUploadableProperties;
import com.microsoft.graph.models.ItemReference;
import com.microsoft.graph.models.UploadSession;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.model.request.data.SendMessageToChannel;
import io.camunda.connector.api.document.Document;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;

public class DocumentHandler {

  private final SendMessageToChannel model;
  private final GraphServiceClient graphClient;

  public DocumentHandler(GraphServiceClient graphClient, SendMessageToChannel model) {
    this.graphClient = graphClient;
    this.model = model;
  }

  public List<ChatMessageAttachment> handleDocuments() {
    List<String> fileUrls =
        model.documents().stream()
            .filter(Objects::nonNull)
            .map(
                document -> {
                  UploadSession uploadSession =
                      getUploadSession(model.groupId(), document.metadata().getFileName());
                  return uploadDocument(uploadSession, document);
                })
            .filter(Objects::nonNull)
            .toList();

    List<ChatMessageAttachment> attachments = new ArrayList<>();
    fileUrls.forEach(
        fileUrl -> {
          ChatMessageAttachment attachment = new ChatMessageAttachment();
          attachment.setContentUrl(fileUrl);
          attachment.setContentType("reference");
          attachments.add(attachment);
        });
    return attachments;
  }

  private String uploadDocument(UploadSession uploadSession, Document document) {
    try {
      LargeFileUploadTask<DriveItem> largeFileUploadTask =
          new LargeFileUploadTask<>(
              graphClient.getRequestAdapter(),
              uploadSession,
              document.asInputStream(),
              document.metadata().getSize(),
              DriveItem::createFromDiscriminatorValue);
      UploadResult<DriveItem> uploadResult =
          largeFileUploadTask.upload(); // This will retry 3 times
      if (uploadResult.isUploadSuccessful() && uploadResult.itemResponse != null) {
        return uploadResult.itemResponse.getWebUrl();
      } else {
        throw new ConnectorInputException(
            "Failed to upload document " + document.metadata().getFileName() + " with retries",
            new RuntimeException());
      }
    } catch (CancellationException
        | InterruptedException
        | IOException
        | IllegalAccessException
        | NoSuchMethodException
        | InvocationTargetException e) {
      throw new ConnectorException("Error uploading document: " + e.getMessage(), e);
    }
  }

  private UploadSession getUploadSession(String teamID, String filename) {
    DriveItem driveItem = getDriveItem(teamID);
    String driveItemId = getDriveItemId(driveItem, filename);
    String driveId = getMyDriveId(driveItem);

    CreateUploadSessionPostRequestBody uploadSessionRequest =
        new CreateUploadSessionPostRequestBody();
    DriveItemUploadableProperties properties = new DriveItemUploadableProperties();
    properties.getAdditionalData().put("@microsoft.graph.conflictBehavior", "rename");
    uploadSessionRequest.setItem(properties);

    UploadSession uploadSession =
        Optional.ofNullable(
                graphClient
                    .drives()
                    .byDriveId(driveId)
                    .items()
                    .byDriveItemId(driveItemId)
                    .createUploadSession()
                    .post(uploadSessionRequest))
            .orElseThrow(
                () -> new IllegalStateException("Upload session is null, cannot proceed."));
    return uploadSession;
  }

  private DriveItem getDriveItem(String teamID) {
    return Optional.ofNullable(
            graphClient
                .teams()
                .byTeamId(teamID)
                .channels()
                .byChannelId(model.channelId())
                .filesFolder()
                .get())
        .orElseThrow(() -> new IllegalStateException("Drive ID is null, cannot proceed."));
  }

  private String getMyDriveId(DriveItem channelFolder) {
    return Optional.ofNullable(channelFolder.getParentReference())
        .map(ItemReference::getDriveId)
        .orElseThrow(() -> new IllegalStateException("Drive ID is null, cannot proceed."));
  }

  private String getDriveItemId(DriveItem driveItem, String filename) {
    String channelName =
        Optional.ofNullable(driveItem.getName())
            .orElseThrow(() -> new IllegalStateException("Channel name is null, cannot proceed."));
    return "root:/" + channelName + "/" + filename + ":";
  }
}
