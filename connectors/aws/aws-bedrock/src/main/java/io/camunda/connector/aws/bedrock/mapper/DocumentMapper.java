/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.mapper;

import io.camunda.connector.aws.bedrock.util.FileUtil;
import io.camunda.connector.api.document.Document;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tika.mime.MimeTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.SdkPojo;
import software.amazon.awssdk.services.bedrockruntime.model.*;

public class DocumentMapper {

  public static final String UNSUPPORTED_DOC_TYPE_MSG = "Unsupported document type: ";
  public static final String UNSUPPORTED_CONTENT_TYPE_MSG = "Unsupported document content type: ";
  private static final Logger LOGGER = LoggerFactory.getLogger(DocumentMapper.class);

  public DocumentMapper() {}

  public SdkPojo mapToFileBlock(Document document) {
    Pair<String, String> nameTypePair =
        FileUtil.defineNameAndType(document.metadata().getFileName());
    String fileName = nameTypePair.getLeft();
    String fileType = nameTypePair.getRight();
    String contentType = document.metadata().getContentType();

    try {
      fileType = fileType.isEmpty() ? FileUtil.defineType(contentType) : fileType;
    } catch (MimeTypeException e) {
      String errorMsg = UNSUPPORTED_CONTENT_TYPE_MSG + contentType;
      LOGGER.debug(errorMsg);
      throw new RuntimeException(errorMsg, e);
    }

    var bytes = document.asByteArray();
    var imageFormat = ImageFormat.fromValue(fileType);

    if (!imageFormat.equals(ImageFormat.UNKNOWN_TO_SDK_VERSION)) {
      return mapToImag(bytes, imageFormat);
    }

    var documentFormat = DocumentFormat.fromValue(fileType);

    if (!documentFormat.equals(DocumentFormat.UNKNOWN_TO_SDK_VERSION)) {
      return mapToDocument(bytes, documentFormat, fileName);
    }

    String unsupportedDocumentMsg = UNSUPPORTED_DOC_TYPE_MSG + fileName;

    LOGGER.debug(unsupportedDocumentMsg);
    throw new IllegalArgumentException(unsupportedDocumentMsg);
  }

  private ImageBlock mapToImag(byte[] bytes, ImageFormat imageFormat) {
    return ImageBlock.builder()
        .source(ImageSource.builder().bytes(SdkBytes.fromByteArray(bytes)).build())
        .format(imageFormat)
        .build();
  }

  private DocumentBlock mapToDocument(
      byte[] bytes, DocumentFormat documentFormat, String fileName) {
    return DocumentBlock.builder()
        .source(DocumentSource.builder().bytes(SdkBytes.fromByteArray(bytes)).build())
        .format(documentFormat)
        .name(fileName)
        .build();
  }
}
