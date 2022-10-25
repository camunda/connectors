package io.camunda.connector.inbound.security.signature;

public enum HMACAlgoCustomerChoice {

    sha_1("HmacSHA1", "sha1"),
    sha_256("HmacSHA256", "sha256"),
    sha_512("HmacSHA512", "sha512");

    private final String algoReference;
    private final String tag;

    HMACAlgoCustomerChoice(final String algoReference, final String tag) {
        this.algoReference = algoReference;
        this.tag = tag;
    }

    public String getAlgoReference() {
        return algoReference;
    }

    public String getTag() {
        return tag;
    }
}
