package io.camunda.connector.awss3.in;

import io.camunda.connector.fileapi.model.RequestData;
import io.camunda.connector.awss3.in.model.ConnectorRequest;

import java.util.Objects;

public class RequestMapper {

    public static RequestData mapRequest(ConnectorRequest request) {
        return RequestData.builder()
                .authenticationKey(request.getAuthentication().getAccessKey())
                .authenticationSecret(request.getAuthentication().getSecretKey())
                .region(request.getRequestDetails().getRegion())
                .bucket(request.getRequestDetails().getBucketName())
                .key(request.getRequestDetails().getObjectKey())
                .filePath(
                        // fallback to objectKey
                        Objects.requireNonNullElse(
                                request.getRequestDetails().getFilePath(),
                                request.getRequestDetails().getObjectKey()
                        )
                )
                .contentType(request.getRequestDetails().getContentType())
                .build();
    }

}
