/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack.outbound.caller;

import static java.util.stream.Collectors.toList;

import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.files.FilesCompleteUploadExternalRequest;
import com.slack.api.methods.request.files.FilesCompleteUploadExternalRequest.FileDetails;
import com.slack.api.methods.request.files.FilesGetUploadURLExternalRequest;
import com.slack.api.methods.response.files.FilesCompleteUploadExternalResponse;
import com.slack.api.methods.response.files.FilesGetUploadURLExternalResponse;
import com.slack.api.model.File;
import com.slack.api.util.http.SlackHttpClient;
import io.camunda.connector.api.document.Document;
import java.io.IOException;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileUploader {

  public static final String GET_EXTERNAL_URL_EX = "Error during filesGetUploadURLExternal call";
  public static final String EXTERNAL_URL_CALL_EX = "Error during external call: ";
  public static final String COMPLETE_UPLOAD_CALL_EX =
      "Error during filesCompleteUploadExternal call";

  private static final Logger LOGGER = LoggerFactory.getLogger(FileUploader.class);

  private MethodsClient methodsClient;

  public FileUploader(MethodsClient methodsClient) {
    this.methodsClient = methodsClient;
  }

  /**
   * Uploads the documents to Slack without sharing them. Slack keeps uploaded files private to the
   * app until {@link #shareFiles} completes the upload with a channel.
   */
  public List<FileDetails> uploadDocuments(List<Document> documents) {
    return documents.stream()
        .map(doc -> {
          try {
            return this.uploadDocument(methodsClient, doc);
          } catch (SlackApiException | IOException e) {
            throw new RuntimeException(e);
          }
        })
        .collect(toList());
  }

  private FileDetails uploadDocument(MethodsClient methodsClient, Document document)
      throws SlackApiException, IOException {
    FilesGetUploadURLExternalResponse externalURLUploadResponse =
        getFileUploadURL(methodsClient, document);
    uploadFileByURL(externalURLUploadResponse, document);
    return FileDetails.builder()
        .id(externalURLUploadResponse.getFileId())
        .title(document.metadata().getFileName())
        .build();
  }

  /**
   * Completes the uploads and shares the files in the given channel, inside the given thread when
   * one is set. Slack posts them as a single message.
   */
  public List<File> shareFiles(List<FileDetails> files, String channelId, String threadTs)
      throws SlackApiException, IOException {
    var request = FilesCompleteUploadExternalRequest.builder().files(files).channelId(channelId);
    if (StringUtils.isNotBlank(threadTs)) {
      request.threadTs(threadTs);
    }
    FilesCompleteUploadExternalResponse completeUploadResp =
        methodsClient.filesCompleteUploadExternal(request.build());

    if (completeUploadResp.isOk()) {
      return completeUploadResp.getFiles() == null ? List.of() : completeUploadResp.getFiles();
    }
    String msg = COMPLETE_UPLOAD_CALL_EX + "\n Errors: " + completeUploadResp.getError();
    LOGGER.error(msg);
    throw new RuntimeException(msg);
  }

  private FilesGetUploadURLExternalResponse getFileUploadURL(
      MethodsClient methodsClient, Document document) throws SlackApiException, IOException {
    var filesGetUploadURLExternalRequest =
        FilesGetUploadURLExternalRequest.builder()
            .filename(document.metadata().getFileName())
            .length(document.asByteArray().length)
            .build();
    return methodsClient.filesGetUploadURLExternal(filesGetUploadURLExternalRequest);
  }

  private void uploadFileByURL(
      FilesGetUploadURLExternalResponse uploadFileURLResp, Document document) throws IOException {
    if (!uploadFileURLResp.isOk()) {
      String msg = GET_EXTERNAL_URL_EX + "\n Errors: " + uploadFileURLResp.getError();
      LOGGER.error(msg);
      throw new RuntimeException(msg);
    }
    var config = methodsClient.getSlackHttpClient().getConfig();
    OkHttpClient okHttpClient = SlackHttpClient.buildOkHttpClient(config);

    var request =
        new Request.Builder()
            .url(uploadFileURLResp.getUploadUrl())
            .post(RequestBody.create(document.asByteArray()))
            .build();

    try (Response directCallResp = okHttpClient.newCall(request).execute()) {
      if (directCallResp.code() != 200) {
        String msg = EXTERNAL_URL_CALL_EX + directCallResp.message();
        LOGGER.error(msg);
        throw new RuntimeException(msg);
      }
    }
  }

  public MethodsClient getMethodsClient() {
    return methodsClient;
  }

  public void setMethodsClient(MethodsClient methodsClient) {
    this.methodsClient = methodsClient;
  }
}
