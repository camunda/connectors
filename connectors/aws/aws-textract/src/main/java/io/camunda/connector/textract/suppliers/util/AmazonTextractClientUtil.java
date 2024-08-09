package io.camunda.connector.textract.suppliers.util;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.textract.AmazonTextract;
import com.amazonaws.services.textract.AmazonTextractAsync;
import com.amazonaws.services.textract.AmazonTextractAsyncClientBuilder;
import com.amazonaws.services.textract.AmazonTextractClientBuilder;
import io.camunda.connector.aws.CredentialsProviderSupport;
import io.camunda.connector.textract.model.TextractRequest;
import org.apache.commons.lang3.tuple.Pair;

public class AmazonTextractClientUtil {

    private AmazonTextractClientUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static AmazonTextract getSyncTextractClient(final TextractRequest request) {
        final Pair<AWSCredentialsProvider, String> credRegion = prepareCredentialRegionPair(request);

        return getTextractClient(AmazonTextractClientBuilder.standard(), credRegion.getLeft(), credRegion.getRight());
    }

    public static AmazonTextractAsync getAsyncTextractClient(final TextractRequest request) {
        final Pair<AWSCredentialsProvider, String> credRegion = prepareCredentialRegionPair(request);

        return (AmazonTextractAsync) getTextractClient(AmazonTextractAsyncClientBuilder.standard(), credRegion.getLeft(),
                credRegion.getRight());
    }

    public static AmazonTextract getTextractClient(final AwsClientBuilder awsClientBuilder, final AWSCredentialsProvider credentials, final String region) {
        return AmazonTextractClientBuilder.standard()
                .withRegion(region)
                .withCredentials(credentials)
                .build();
    }

    private static Pair<AWSCredentialsProvider, String> prepareCredentialRegionPair(final TextractRequest request) {
        final AWSCredentialsProvider credentials = CredentialsProviderSupport.credentialsProvider(request);
        final String region = request.getConfiguration().region();
        return Pair.of(credentials, region);
    }
}
