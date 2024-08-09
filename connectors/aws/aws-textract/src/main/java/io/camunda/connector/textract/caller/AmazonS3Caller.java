package io.camunda.connector.textract.caller;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3Object;

public class AmazonS3Caller {
    private final AmazonS3 amazonS3Client;

    public AmazonS3Caller(AmazonS3 amazonS3) {
        this.amazonS3Client = amazonS3;
    }
    
    public S3Object getS3Object(final String bucketName, final String key){
        return amazonS3Client.getObject(bucketName, key);
    }
}
