package io.camunda.connector.awss3.out.cloud;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

public class CloudClientFactory {

    private String endpointOverride;

    public CloudClientFactory() {
    }

    public CloudClientFactory(String endpointOverride) {
        this.endpointOverride = endpointOverride;
    }

    private static final Logger logger = LoggerFactory.getLogger(CloudClientFactory.class);

    public S3Client createClient(String accessKey, String secretKey, String region) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        logger.info("Initialized AWS client for region: {}", region);

        S3ClientBuilder builder = S3Client.builder()
                .credentialsProvider(() -> StaticCredentialsProvider.create(credentials).resolveCredentials())
                .region(Region.of(region));

        if (endpointOverride != null && !endpointOverride.isBlank()) {
            logger.info("AWS endpoint override: {}", endpointOverride);
            builder.endpointOverride(URI.create(endpointOverride));
        }
        return builder.build();
    }

}
