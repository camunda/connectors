package io.camunda.connector.awss3.out.cloud;

import io.camunda.connector.fileapi.RemoteFileCommand;
import io.camunda.connector.fileapi.model.FileContent;
import io.camunda.connector.fileapi.model.RequestData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;

public class CloudFileAdapter implements RemoteFileCommand {

    private static final Logger logger = LoggerFactory.getLogger(CloudFileAdapter.class);

    private final CloudClientFactory clientFactory;

    public CloudFileAdapter(CloudClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    public void deleteFile(RequestData requestData) {
        try (S3Client s3Client = clientFactory.createClient(requestData.getAuthenticationKey(), requestData.getAuthenticationSecret(), requestData.getRegion())) {
            DeleteObjectRequest awsRequest = DeleteObjectRequest.builder()
                    .bucket(requestData.getBucket())
                    .key(requestData.getKey())
                    .build();
            logger.info("Delete object: {}", requestData.getBucket() + "/" + requestData.getKey());
            logger.debug("request {}", awsRequest);
            DeleteObjectResponse response = s3Client.deleteObject(awsRequest);
            logger.info("Object deleted: {}", requestData.getBucket() + "/" + requestData.getKey());
            logger.debug("response {}", response);
        }
    }

    public void putFile(RequestData requestData, FileContent fileContent) {
        try (S3Client s3Client = clientFactory.createClient(requestData.getAuthenticationKey(), requestData.getAuthenticationSecret(), requestData.getRegion())) {
            PutObjectRequest awsRequest = PutObjectRequest.builder()
                    .bucket(requestData.getBucket())
                    .key(requestData.getKey())
                    .contentType(fileContent.getContentType())
                    .contentLength(fileContent.getContentLength())
                    .build();
            logger.info("Put object: {}", requestData.getBucket() + "/" + requestData.getKey());
            logger.debug("request {}", awsRequest);
            PutObjectResponse awsResponse = s3Client.putObject(awsRequest, RequestBody.fromBytes(fileContent.getContent()));
            logger.info("Object put: {}", requestData.getBucket() + "/" + requestData.getKey());
            logger.debug("response {}", awsResponse);
        }
    }

    public FileContent getFile(RequestData requestData) throws IOException {
        try (S3Client s3Client = clientFactory.createClient(requestData.getAuthenticationKey(), requestData.getAuthenticationSecret(), requestData.getRegion())) {
            GetObjectRequest awsRequest = GetObjectRequest.builder()
                    .bucket(requestData.getBucket())
                    .key(requestData.getKey())
                    .build();
            logger.info("Get object: {}", requestData.getBucket() + "/" + requestData.getKey());
            logger.debug("request {}", awsRequest);
            try (ResponseInputStream<GetObjectResponse> object = s3Client.getObject(awsRequest)) {
                FileContent result = FileContent.builder()
                        .content(object.readAllBytes())
                        .contentType(object.response().contentType())
                        .contentLength(object.response().contentLength())
                        .build();
                logger.info("Object received: {}", requestData.getBucket() + "/" + requestData.getKey());
                logger.debug("response {}", object.response());
                return result;
            }
        }
    }

}
