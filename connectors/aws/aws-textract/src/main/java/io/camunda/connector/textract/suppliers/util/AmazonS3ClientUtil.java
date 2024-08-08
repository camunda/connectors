package io.camunda.connector.textract.suppliers.util;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import io.camunda.connector.aws.CredentialsProviderSupport;
import io.camunda.connector.textract.model.TextractRequest;


public class AmazonS3ClientUtil {
    private AmazonS3ClientUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");

    }

    public static AmazonS3 getAmazonS3Client(final TextractRequest request){
        final AWSCredentialsProvider credentials = CredentialsProviderSupport.credentialsProvider(request);
        final String region = request.getConfiguration().region();
        return getAmazonS3Client(credentials, region);
    }
    public static AmazonS3 getAmazonS3Client(final AWSCredentialsProvider credentials, final String region){
        return AmazonS3ClientBuilder.standard()
                .withCredentials(credentials)
                .withRegion(region)
                .build();
    }
}
