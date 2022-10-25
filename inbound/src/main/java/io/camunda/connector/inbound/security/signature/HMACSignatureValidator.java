package io.camunda.connector.inbound.security.signature;

import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StreamUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

// TODO: add URL signing and Base64 format
public class HMACSignatureValidator {

    private static final Logger LOG = LoggerFactory.getLogger(HMACSignatureValidator.class);

    private final byte[] requestBody;
    private final Map<String, String> headers;
    private final String hmacHeader;
    private final String hmacSecretKey;
    private final HMACAlgoCustomerChoice hmacAlgo;

    public HMACSignatureValidator(
            final byte[] requestBody,
            final Map<String, String> headers,
            final String hmacHeader,
            final String hmacSecretKey,
            final HMACAlgoCustomerChoice hmacAlgo) {
        this.requestBody = requestBody;
        this.headers = headers;
        this.hmacHeader = hmacHeader;
        this.hmacSecretKey = hmacSecretKey;
        this.hmacAlgo = hmacAlgo;
    }

    public boolean isRequestValid() throws NoSuchAlgorithmException, InvalidKeyException {
        final String providedHmac = headers.get(hmacHeader);

        LOG.debug("Given HMAC from webhook call: {}", providedHmac);

        byte[] signedEntity = requestBody;

        Mac sha256_HMAC = Mac.getInstance(hmacAlgo.getAlgoReference());
        SecretKeySpec secret_key =
                new SecretKeySpec(hmacSecretKey.getBytes(StandardCharsets.UTF_8), hmacAlgo.getAlgoReference());
        sha256_HMAC.init(secret_key);
        byte[] expectedHmac = sha256_HMAC.doFinal(signedEntity);

        // Some webhooks produce short HMAC message, e.g. aabbcc...
        String expectedShortHmacString = Hex.encodeHexString(expectedHmac);
        // The other produce longer version, like sha256=aabbcc...
        String expectedLongHmacString = hmacAlgo.getTag() + "=" + expectedShortHmacString;
        LOG.debug("Computed HMAC from webhook body: {}, {}", expectedShortHmacString, expectedLongHmacString);

        return providedHmac.equals(expectedShortHmacString) || providedHmac.equals(expectedLongHmacString);
    }

}
