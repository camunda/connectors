package io.camunda.connector.fileapi.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RequestData {

    private String authenticationKey;
    private String authenticationSecret;
    private String bucket;
    private String key;
    private String region;
    private String filePath;
    private String contentType;

}
