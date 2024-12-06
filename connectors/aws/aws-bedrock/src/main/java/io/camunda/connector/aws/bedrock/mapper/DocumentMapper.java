/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.mapper;

import io.camunda.connector.aws.bedrock.model.format.DocumentBlockFormat;
import io.camunda.connector.aws.bedrock.model.format.ImageBlockFormat;
import io.camunda.document.Document;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.model.DocumentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.DocumentSource;
import software.amazon.awssdk.services.bedrockruntime.model.ImageBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ImageSource;

public final class DocumentMapper {

  public static final String UNSUPPORTED_DOC_TYPE_MSG = "Unsupported document type: ";
  private static final Logger LOGGER = LoggerFactory.getLogger(DocumentMapper.class);

  private DocumentMapper() {}

  public static List<Object> mapToDocumentBlocks(List<Document> documents) {
    if (documents == null || documents.isEmpty()) {
      return List.of();
    }
    return documents.stream().map(DocumentMapper::mapToDocumentBlock).toList();
  }

  private static Object mapToDocumentBlock(Document document) {
    String fileTipeSeparator = "/";
    String contentType = document.metadata().getContentType();
    var docType = StringUtils.substringAfter(contentType, fileTipeSeparator);
    var bytes = document.asByteArray();

    if (isImageType(docType)) {
      return mapToImag(bytes);
    }

    if (isDocumentType(docType)) {
      return mapToDocument(bytes);
    }

    LOGGER.debug(UNSUPPORTED_DOC_TYPE_MSG + document);
    throw new IllegalArgumentException(UNSUPPORTED_DOC_TYPE_MSG + document);
  }

  private static ImageBlock mapToImag(byte[] bytes) {
    return ImageBlock.builder()
        .source(ImageSource.builder().bytes(SdkBytes.fromByteArray(bytes)).build())
        .build();
  }

  private static DocumentBlock mapToDocument(byte[] bytes) {
    return DocumentBlock.builder()
        .source(DocumentSource.builder().bytes(SdkBytes.fromByteArray(bytes)).build())
        .build();
  }

  private static boolean isImageType(String type) {
    return Arrays.stream(ImageBlockFormat.values())
        .anyMatch(imageFormat -> imageFormat.name().equalsIgnoreCase(type));
  }

  private static boolean isDocumentType(String type) {
    return Arrays.stream(DocumentBlockFormat.values())
        .anyMatch(docFormat -> docFormat.name().equalsIgnoreCase(type));
  }
}
