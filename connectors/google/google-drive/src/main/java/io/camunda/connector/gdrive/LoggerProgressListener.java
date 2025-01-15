/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive;

import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resumable upload official example: <a
 * href="https://developers.google.com/api-client-library/java/google-api-java-client/media-upload#implementation">...</a>
 */
public class LoggerProgressListener implements MediaHttpUploaderProgressListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(LoggerProgressListener.class);

  @Override
  public void progressChanged(MediaHttpUploader uploader) throws IOException {
    switch (uploader.getUploadState()) {
      case NOT_STARTED:
        LOGGER.debug("Upload not started");
        break;
      case INITIATION_STARTED:
        LOGGER.debug("Initiation has started!");
        break;
      case INITIATION_COMPLETE:
        LOGGER.debug("Initiation is complete!");
        break;
      case MEDIA_IN_PROGRESS:
        LOGGER.debug("Upload progress: {}", uploader.getProgress());
        break;
      case MEDIA_COMPLETE:
        LOGGER.debug("Upload is complete!");
    }
  }
}
