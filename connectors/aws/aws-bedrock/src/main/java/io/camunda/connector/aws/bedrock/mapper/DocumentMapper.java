/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.mapper;

import io.camunda.document.Document;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.util.List;

public final class DocumentMapper {

    public static final String UNSUPPORTED_DOC_TYPE_MSG = "Unsupported document type: ";
    public static final String WRONG_FILE_MSG = "Provide wrong file name: ";
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentMapper.class);

    public DocumentMapper() {
    }

    public static List<Object>  mapToDocumentBlocks(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        return documents.stream().map(DocumentMapper::mapToDocumentBlock).toList();
    }

    private static Object mapToDocumentBlock(Document document) {
        var nameTypePair = splitFileName(document);
        String fileName = nameTypePair.getLeft();
        String fileType = nameTypePair.getRight();
        var bytes = document.asByteArray();

        var imageFormat = ImageFormat.fromValue(fileType);
        var documentFormat = DocumentFormat.fromValue(fileType);

        if (!imageFormat.equals(ImageFormat.UNKNOWN_TO_SDK_VERSION)) {
            return mapToImag(bytes, imageFormat);
        }

        if (!documentFormat.equals(DocumentFormat.UNKNOWN_TO_SDK_VERSION)) {
            return mapToDocument(bytes, documentFormat, fileName);
        }

        LOGGER.debug(UNSUPPORTED_DOC_TYPE_MSG + fileName);
        throw new IllegalArgumentException(UNSUPPORTED_DOC_TYPE_MSG + fileName);
    }

    private static ImageBlock mapToImag(byte[] bytes, ImageFormat imageFormat) {
        return ImageBlock.builder()
                .source(ImageSource.builder().bytes(SdkBytes.fromByteArray(bytes)).build())
                .format(imageFormat)
                .build();
    }

    private static DocumentBlock mapToDocument(byte[] bytes, DocumentFormat documentFormat, String fileName) {
        return DocumentBlock.builder()
                .source(DocumentSource.builder().bytes(SdkBytes.fromByteArray(bytes)).build())
                .format(documentFormat)
                .name(fileName)
                .build();
    }

    private static Pair<String, String> splitFileName(Document document) {
        try {
            String fileTypeSeparator = "\\.";
            String fullName = document.metadata().getFileName();
            String[] nameParts = fullName.split(fileTypeSeparator);

            String fileName = nameParts[0];
            String fileType = nameParts[1];

            return Pair.of(fileName, fileType);
        } catch (Exception e) {
            String fileName = document.metadata().getFileName();
            LOGGER.debug(UNSUPPORTED_DOC_TYPE_MSG + fileName);
            throw new IllegalArgumentException(WRONG_FILE_MSG + fileName);
        }
    }
}
