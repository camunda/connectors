/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connectors.soap;

import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Date;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.openssl.PEMWriter;
import org.bouncycastle.x509.X509V1CertificateGenerator;

public class CertificateUtil {

  public record CertificateHolder(String certificate, String privateKey) {}

  public static CertificateHolder generateSelfSignedCertificate() {
    try {

      // yesterday
      Date validityBeginDate = new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000);
      // in 2 years
      Date validityEndDate = new Date(System.currentTimeMillis() + 2 * 365 * 24 * 60 * 60 * 1000);

      // GENERATE THE PUBLIC/PRIVATE RSA KEY PAIR
      KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
      keyPairGenerator.initialize(1024, new SecureRandom());

      java.security.KeyPair keyPair = keyPairGenerator.generateKeyPair();

      // GENERATE THE X509 CERTIFICATE
      X509V1CertificateGenerator certGen = new X509V1CertificateGenerator();
      X500Principal dnName = new X500Principal("CN=John Doe");

      certGen.setSerialNumber(BigInteger.valueOf(System.currentTimeMillis()));
      certGen.setSubjectDN(dnName);
      certGen.setIssuerDN(dnName); // use the same
      certGen.setNotBefore(validityBeginDate);
      certGen.setNotAfter(validityEndDate);
      certGen.setPublicKey(keyPair.getPublic());
      certGen.setSignatureAlgorithm("SHA256WithRSA");

      X509Certificate cert = certGen.generate(keyPair.getPrivate());

      StringWriter certWriter = new StringWriter();
      PEMWriter pemCertWriter = new PEMWriter(certWriter);
      pemCertWriter.writeObject(cert);
      pemCertWriter.flush();

      StringWriter privateKeyWriter = new StringWriter();
      PEMWriter pemPrivateKeyWriter = new PEMWriter(privateKeyWriter);
      pemPrivateKeyWriter.writeObject(keyPair.getPrivate());
      pemPrivateKeyWriter.flush();

      return new CertificateHolder(certWriter.toString(), privateKeyWriter.toString());
    } catch (Exception e) {
      throw new RuntimeException("Error while generating certificate", e);
    }
  }
}
