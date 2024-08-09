package io.camunda.connector.textract.caller;

import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.textract.AmazonTextract;
import com.amazonaws.services.textract.model.AnalyzeDocumentRequest;
import com.amazonaws.services.textract.model.AnalyzeDocumentResult;
import com.amazonaws.services.textract.model.Document;
import com.amazonaws.services.textract.model.FeatureType;
import com.amazonaws.util.IOUtils;
import io.camunda.connector.textract.model.TextractRequestData;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

public class AmazonAsyncTextractCaller {

    private final AmazonS3Caller amazonS3Caller;

    private final AmazonTextract amazonTextractAsync;

    public AmazonAsyncTextractCaller(AmazonS3Caller amazonS3Caller, AmazonTextract amazonTextractAsync) {
        this.amazonS3Caller = amazonS3Caller;
        this.amazonTextractAsync = amazonTextractAsync;
    }

    public AnalyzeDocumentResult callTextract(final TextractRequestData request) throws IOException {
        final S3Object s3Object = amazonS3Caller.getS3Object(request.documentS3Bucket(), request.documentName());

        final ByteBuffer byteBuffer;
        try (final InputStream inputStream = s3Object.getObjectContent()) {
            byteBuffer = ByteBuffer.wrap(IOUtils.toByteArray(inputStream));
        }

        final Document document = new Document().withBytes(byteBuffer);

        final AnalyzeDocumentRequest analyzeDocumentRequest = new AnalyzeDocumentRequest()
                .withFeatureTypes(this.prepareFeatureTypes(request))
                .withDocument(document);
        
        return amazonTextractAsync.analyzeDocument(analyzeDocumentRequest);
    }

    private Set<String> prepareFeatureTypes(final TextractRequestData request) {
        final Set<String> types = new HashSet<>();
        if (request.analyzeForms()) types.add(FeatureType.FORMS.name());
        if (request.analyzeLayout()) types.add(FeatureType.LAYOUT.name());
        if (request.analyzeSignatures()) types.add(FeatureType.SIGNATURES.name());
        if (request.analyzeTables()) types.add(FeatureType.TABLES.name());
        return types;
    }
}
