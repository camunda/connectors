/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connectors.soap.client;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;

import io.camunda.connectors.soap.SoapConnectorInput.Authentication;
import io.camunda.connectors.soap.SoapConnectorInput.Authentication.Signature;
import io.camunda.connectors.soap.SoapConnectorInput.Authentication.Signature.Certificate.KeystoreCertificate;
import io.camunda.connectors.soap.SoapConnectorInput.Authentication.Signature.Certificate.SingleCertificate;
import io.camunda.connectors.soap.SoapConnectorInput.Authentication.UsernameToken;
import io.camunda.connectors.soap.SoapConnectorInput.Version;
import io.camunda.connectors.soap.SoapConnectorInput.Version._1_1;
import io.camunda.connectors.soap.SoapConnectorInput.Version._1_2;
import io.camunda.connectors.soap.SoapConnectorInput.YesNo;
import io.camunda.connectors.soap.xml.XmlUtilities;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import org.apache.commons.lang3.StringUtils;
import org.apache.wss4j.common.WSS4JConstants;
import org.apache.wss4j.common.crypto.Crypto;
import org.apache.wss4j.common.crypto.Merlin;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.oxm.Marshaller;
import org.springframework.oxm.Unmarshaller;
import org.springframework.oxm.XmlMappingException;
import org.springframework.ws.WebServiceMessageFactory;
import org.springframework.ws.client.core.support.WebServiceGatewaySupport;
import org.springframework.ws.client.support.interceptor.ClientInterceptor;
import org.springframework.ws.soap.SoapVersion;
import org.springframework.ws.soap.client.core.SoapActionCallback;
import org.springframework.ws.soap.saaj.SaajSoapMessageFactory;
import org.springframework.ws.soap.security.wss4j2.Wss4jSecurityInterceptor;
import org.springframework.ws.soap.security.wss4j2.support.CryptoFactoryBean;
import org.springframework.ws.transport.WebServiceMessageSender;
import org.springframework.ws.transport.http.ClientHttpRequestMessageSender;
import org.w3c.dom.Node;

public class SpringSoapClient implements SoapClient {
  private static final String SINGLE_CERTIFICATE_ALIAS = "user";
  private static final String SINGLE_CERTIFICATE_PASSWORD = "pw";

  @Override
  public String sendSoapRequest(
      String serviceUrl,
      Version soapVersion,
      String soapHeader,
      String soapBody,
      Authentication authentication,
      Integer connectionTimeoutInSeconds,
      Map<String, String> namespaces)
      throws Exception {
    final var requestFactory = buildRequestFactory(connectionTimeoutInSeconds);
    final var messageSender = buildMessageSender(requestFactory);
    final var messageFactory = buildMessageFactory(soapVersion);
    final var marshaller = buildMarshaller(namespaces);
    final var unmarshaller = buildUnmarshaller(namespaces);
    ClientInterceptor[] interceptors = buildInterceptors(soapHeader, authentication, namespaces);
    WebServiceClient wsClient = new WebServiceClient();
    wsClient.setDefaultUri(serviceUrl);
    wsClient.setMessageSender(messageSender);
    wsClient.setInterceptors(interceptors);
    wsClient.setMessageFactory(messageFactory);
    wsClient.setMarshaller(marshaller);
    wsClient.setUnmarshaller(unmarshaller);
    wsClient.afterPropertiesSet();
    return wsClient.sendSoapRequestFromFactory(soapBody, soapVersion);
  }

  private Marshaller buildMarshaller(Map<String, String> namespaces) {
    return new StringBodyMarshaller(namespaces);
  }

  private Unmarshaller buildUnmarshaller(Map<String, String> namespaces) {
    return new StringBodyMarshaller(namespaces);
  }

  private WebServiceMessageFactory buildMessageFactory(Version soapVersion) {
    final var messageFactory = new SaajSoapMessageFactory();
    if (soapVersion instanceof _1_1) {
      messageFactory.setSoapVersion(SoapVersion.SOAP_11);
    }
    if (soapVersion instanceof _1_2) {
      messageFactory.setSoapVersion(SoapVersion.SOAP_12);
    }
    messageFactory.afterPropertiesSet();
    return messageFactory;
  }

  private WebServiceMessageSender buildMessageSender(ClientHttpRequestFactory requestFactory) {
    final var messageSender = new ClientHttpRequestMessageSender();
    messageSender.setRequestFactory(requestFactory);
    return messageSender;
  }

  private ClientHttpRequestFactory buildRequestFactory(Integer connectionTimeoutInSeconds) {
    final var requestFactory = new SimpleClientHttpRequestFactory();
    if (connectionTimeoutInSeconds != null && connectionTimeoutInSeconds > 0) {
      requestFactory.setConnectTimeout(connectionTimeoutInSeconds * 1000);
      requestFactory.setReadTimeout(connectionTimeoutInSeconds * 1000);
    }
    return requestFactory;
  }

  private ClientInterceptor[] buildInterceptors(
      String soapHeader, Authentication authentication, Map<String, String> namespaces) {
    List<ClientInterceptor> interceptorList = new ArrayList<>();
    handleAuthentication(authentication).ifPresent(interceptorList::add);
    handleSoapHeader(soapHeader, namespaces).ifPresent(interceptorList::add);
    interceptorList.add(new LoggingInterceptor());
    return interceptorList.toArray(new ClientInterceptor[0]);
  }

  private Optional<ClientInterceptor> handleSoapHeader(
      String soapHeader, Map<String, String> namespaces) {
    if (StringUtils.isEmpty(soapHeader)) {
      return empty();
    }
    return of(new HeaderClientInterceptor(soapHeader, namespaces));
  }

  private Optional<ClientInterceptor> handleAuthentication(Authentication authentication) {
    final var securityInterceptor = new Wss4jSecurityInterceptor();
    if (authentication instanceof UsernameToken usernameToken) {
      securityInterceptor.setSecurementActions(WSS4JConstants.USERNAME_TOKEN_LN);
      securityInterceptor.setSecurementUsername(usernameToken.username());
      if (YesNo.Yes.equals(usernameToken.encoded())) {
        try {
          securityInterceptor.setSecurementPassword(
              new String(
                  MessageDigest.getInstance("SHA-1").digest(usernameToken.password().getBytes())));
        } catch (NoSuchAlgorithmException e) {
          throw new RuntimeException(e);
        }
        securityInterceptor.setSecurementPasswordType(WSS4JConstants.PW_DIGEST);
      } else {
        securityInterceptor.setSecurementPassword(usernameToken.password());
        securityInterceptor.setSecurementPasswordType(WSS4JConstants.PW_TEXT);
      }
    } else if (authentication instanceof Signature signature) {
      securityInterceptor.setEnableSignatureConfirmation(true);
      securityInterceptor.setSecurementSignatureKeyIdentifier("DirectReference");
      if (signature.timestamp() != null) {
        securityInterceptor.setSecurementActions(
            String.format("%s %s", WSS4JConstants.TIMESTAMP_TOKEN_LN, WSS4JConstants.SIG_LN));
        securityInterceptor.setSecurementTimeToLive(signature.timestamp());
      } else {
        securityInterceptor.setSecurementActions(String.format("%s", WSS4JConstants.SIG_LN));
      }
      if (signature.certificate() instanceof SingleCertificate singleCertificate) {
        Crypto crypto = cryptoFromSingleCertificate(singleCertificate);
        securityInterceptor.setSecurementUsername(SINGLE_CERTIFICATE_ALIAS);
        securityInterceptor.setSecurementPassword(SINGLE_CERTIFICATE_PASSWORD);
        securityInterceptor.setSecurementSignatureCrypto(crypto);
        securityInterceptor.setValidationSignatureCrypto(crypto);
      } else if (signature.certificate() instanceof KeystoreCertificate keystoreCertificate) {
        Crypto crypto = cryptoFromKeystoreCertificate(keystoreCertificate);
        securityInterceptor.setSecurementUsername(keystoreCertificate.alias());
        securityInterceptor.setSecurementPassword(keystoreCertificate.password());
        securityInterceptor.setSecurementSignatureCrypto(crypto);
        securityInterceptor.setValidationSignatureCrypto(crypto);
      }
      securityInterceptor.setSecurementSignatureParts(
          signature.encryptionParts().stream()
              .map(part -> String.format("{}{%s}%s;", part.namespace(), part.localName()))
              .collect(Collectors.joining("")));
      securityInterceptor.setSecurementMustUnderstand(true);
      ofNullable(signature.digestAlgorithm())
          .ifPresent(securityInterceptor::setSecurementSignatureDigestAlgorithm);
      ofNullable(signature.signatureAlgorithm())
          .ifPresent(securityInterceptor::setSecurementSignatureAlgorithm);
    } else {
      return empty();
    }
    try {
      securityInterceptor.afterPropertiesSet();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return of(securityInterceptor);
  }

  private Crypto cryptoFromSingleCertificate(SingleCertificate certificate) {
    try {
      Merlin merlin = new Merlin();
      KeyStore keyStore = loadKeyStore(certificate);
      merlin.setKeyStore(keyStore);
      merlin.setTrustStore(keyStore);
      return merlin;
    } catch (Exception e) {
      throw new RuntimeException("Error while building crypto", e);
    }
  }

  private KeyStore loadKeyStore(SingleCertificate certificate) {
    try {
      KeyStore keyStore = createEmptyKeyStore();
      X509Certificate publicCert = loadCertificate(certificate.certificate());
      PrivateKey pk = loadPrivateKey(certificate.privateKey());
      keyStore.setCertificateEntry("cert", publicCert);
      keyStore.setKeyEntry(
          SINGLE_CERTIFICATE_ALIAS,
          pk,
          SINGLE_CERTIFICATE_PASSWORD.toCharArray(),
          new Certificate[] {publicCert});
      return keyStore;
    } catch (KeyStoreException e) {
      throw new RuntimeException("Error while loading keystore", e);
    }
  }

  private KeyStore createEmptyKeyStore() {
    try {
      KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      keyStore.load(null, null);
      return keyStore;
    } catch (Exception e) {
      throw new RuntimeException("Error while creating keystore", e);
    }
  }

  private X509Certificate loadCertificate(String certificate) {
    try {
      CertificateFactory factory = CertificateFactory.getInstance("X.509");
      return (X509Certificate)
          factory.generateCertificate(new ByteArrayInputStream(certificate.getBytes()));
    } catch (Exception e) {
      throw new RuntimeException("Error while loading certificate", e);
    }
  }

  private PrivateKey loadPrivateKey(String privateKey) {
    try {
      StringReader reader = new StringReader(privateKey);
      PEMParser pemParser = new PEMParser(reader);
      Object o = pemParser.readObject();
      PrivateKeyInfo pki;
      if (o instanceof PEMKeyPair keyPair) {
        pki = keyPair.getPrivateKeyInfo();
      } else if (o instanceof PrivateKeyInfo privateKeyInfo) {
        pki = privateKeyInfo;
      } else {
        throw new IllegalStateException("Unknown PEM Parser result: " + o);
      }
      JcaPEMKeyConverter conv = new JcaPEMKeyConverter();
      return conv.getPrivateKey(pki);
    } catch (Exception e) {
      throw new RuntimeException("Error while loading privateKey", e);
    }
  }

  private Crypto cryptoFromKeystoreCertificate(KeystoreCertificate keystoreCertificate) {
    ResourceLoader resourceLoader = new DefaultResourceLoader();
    CryptoFactoryBean cryptoFactoryBean = new CryptoFactoryBean();
    cryptoFactoryBean.setKeyStorePassword(keystoreCertificate.keystorePassword());
    try {
      cryptoFactoryBean.setKeyStoreLocation(
          resourceLoader.getResource(keystoreCertificate.keystoreLocation()));
      cryptoFactoryBean.afterPropertiesSet();
      return cryptoFactoryBean.getObject();
    } catch (Exception e) {
      throw new RuntimeException("Error while loading keystore", e);
    }
  }

  public static class WebServiceClient extends WebServiceGatewaySupport {

    public String sendSoapRequestFromFactory(String requestBody, Version soapVersion) {
      if (soapVersion instanceof _1_1 _1_1) {
        return getWebServiceTemplate()
            .marshalSendAndReceive((Object) requestBody, new SoapActionCallback(_1_1.soapAction()))
            .toString();
      }
      return getWebServiceTemplate().marshalSendAndReceive(requestBody).toString();
    }
  }

  public static final class StringBodyMarshaller implements Marshaller, Unmarshaller {
    private final Map<String, String> namespaces;

    public StringBodyMarshaller(Map<String, String> namespaces) {
      this.namespaces = namespaces;
    }

    @Override
    public boolean supports(Class<?> clazz) {
      return String.class.isAssignableFrom(clazz);
    }

    @Override
    public Object unmarshal(Source source) throws IOException, XmlMappingException {
      DOMSource domSource = (DOMSource) source;
      return XmlUtilities.xmlDocumentToString(domSource.getNode().getOwnerDocument(), false, true);
    }

    @Override
    public void marshal(Object graph, Result result) throws IOException, XmlMappingException {
      String body = (String) graph;
      DOMResult domResult = (DOMResult) result;
      registerNamespaces(domResult);
      Node node = domResult.getNode();
      XmlUtilities.appendXmlStringToNode(body, namespaces, node);
    }

    private void registerNamespaces(DOMResult domResult) {
      domResult
          .getNode()
          .getOwnerDocument()
          .getDocumentElement()
          .setAttribute("xmlns:wsse", WSS4JConstants.WSSE_NS);
      domResult
          .getNode()
          .getOwnerDocument()
          .getDocumentElement()
          .setAttribute("xmlns:wsu", WSS4JConstants.WSU_NS);
      ofNullable(namespaces)
          .ifPresent(
              ns ->
                  ns.forEach(
                      (name, uri) -> {
                        domResult
                            .getNode()
                            .getOwnerDocument()
                            .getDocumentElement()
                            .setAttribute("xmlns:" + name, uri);
                      }));
    }
  }
}
