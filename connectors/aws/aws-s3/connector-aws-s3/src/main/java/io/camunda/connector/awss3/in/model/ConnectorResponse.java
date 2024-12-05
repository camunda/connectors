package io.camunda.connector.awss3.in.model;

import io.camunda.connector.fileapi.model.RequestData;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ConnectorResponse {

    private String bucketName;
    private String objectKey;
    private String filePath;
    private String contentType;

    public ConnectorResponse(RequestData request) {
        this.bucketName = request.getBucket();
        this.objectKey = request.getKey();
        this.filePath = request.getFilePath();
        this.contentType = request.getContentType();
    }

}
