/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.client.jakarta;

import jakarta.activation.DataHandler;
import jakarta.mail.Address;
import jakarta.mail.Flags;
import jakarta.mail.Header;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.eclipse.angus.mail.imap.IMAPMessage;

public class TestImapMessage extends IMAPMessage {

  private final String messageId;
  private final String subject;
  private final String from;
  private final List<String> to;
  private final List<String> cc;
  private final OffsetDateTime sentAt;
  private final OffsetDateTime receivedAt;
  private final Integer size;
  private final Multipart body;

  public TestImapMessage(
      String messageId,
      String subject,
      String from,
      List<String> to,
      List<String> cc,
      OffsetDateTime sentAt,
      OffsetDateTime receivedAt,
      Integer size,
      Multipart body) {
    super(null);
    this.messageId = messageId;
    this.subject = subject;
    this.from = from;
    this.to = to;
    this.cc = cc;
    this.sentAt = sentAt;
    this.receivedAt = receivedAt;
    this.size = size;
    this.body = body;
  }

  public static TestMessageBuilder builder() {
    return new TestMessageBuilder();
  }

  @Override
  public Address[] getFrom() throws MessagingException {
    return InternetAddress.parse(String.join(",", from));
  }

  @Override
  public void setFrom(Address address) throws MessagingException {}

  @Override
  public Address[] getRecipients(Message.RecipientType type) {
    return to.stream()
        .map(
            s -> {
              try {
                return new InternetAddress(s);
              } catch (AddressException e) {
                throw new RuntimeException(e);
              }
            })
        .toArray(Address[]::new);
  }

  @Override
  public void setFrom() throws MessagingException {}

  @Override
  public void addFrom(Address[] addresses) throws MessagingException {}

  @Override
  public String getSubject() throws MessagingException {
    return this.subject;
  }

  @Override
  public void setSubject(String subject) throws MessagingException {}

  @Override
  public Date getSentDate() throws MessagingException {
    return null;
  }

  @Override
  public void setSentDate(Date date) throws MessagingException {}

  @Override
  public Date getReceivedDate() throws MessagingException {
    return null;
  }

  @Override
  public Flags getFlags() throws MessagingException {
    return null;
  }

  @Override
  public void setFlags(Flags flag, boolean set) throws MessagingException {}

  @Override
  public Message reply(boolean replyToAll) throws MessagingException {
    return null;
  }

  @Override
  public void saveChanges() throws MessagingException {}

  @Override
  public int getSize() throws MessagingException {
    return Random.from(RandomGenerator.getDefault()).nextInt(10000);
  }

  @Override
  public int getLineCount() throws MessagingException {
    return 0;
  }

  @Override
  public String getContentType() throws MessagingException {
    return "text/plain";
  }

  @Override
  public boolean isMimeType(String mimeType) throws MessagingException {
    return true;
  }

  @Override
  public String getDisposition() throws MessagingException {
    return "";
  }

  @Override
  public void setDisposition(String disposition) throws MessagingException {}

  @Override
  public String getDescription() throws MessagingException {
    return "";
  }

  @Override
  public void setDescription(String description) throws MessagingException {}

  @Override
  public String getFileName() throws MessagingException {
    return "";
  }

  @Override
  public void setFileName(String filename) throws MessagingException {}

  @Override
  public InputStream getInputStream() throws IOException, MessagingException {
    return null;
  }

  @Override
  public DataHandler getDataHandler() throws MessagingException {
    return null;
  }

  @Override
  public void setDataHandler(DataHandler dh) throws MessagingException {}

  @Override
  public Object getContent() throws IOException, MessagingException {
    return this.body;
  }

  @Override
  public void setContent(Multipart mp) throws MessagingException {}

  @Override
  public void setContent(Object obj, String type) throws MessagingException {}

  @Override
  public void setText(String text) throws MessagingException {}

  @Override
  public void writeTo(OutputStream os) throws IOException, MessagingException {}

  @Override
  public String[] getHeader(String header_name) throws MessagingException {
    return this.messageId.split("/");
  }

  @Override
  public void setHeader(String header_name, String header_value) throws MessagingException {}

  @Override
  public void addHeader(String header_name, String header_value) throws MessagingException {}

  @Override
  public void removeHeader(String header_name) throws MessagingException {}

  @Override
  public Enumeration<Header> getAllHeaders() throws MessagingException {
    return new Enumeration<>() {
      @Override
      public boolean hasMoreElements() {
        return false;
      }

      @Override
      public Header nextElement() {
        return null;
      }
    };
  }

  @Override
  public Enumeration<Header> getMatchingHeaders(String[] header_names) throws MessagingException {
    return null;
  }

  @Override
  public Enumeration<Header> getNonMatchingHeaders(String[] header_names)
      throws MessagingException {
    return null;
  }

  public static class TestMessageBuilder {
    private String messageId;
    private String subject;
    private String from;
    private List<String> strings;
    private List<String> cc;
    private OffsetDateTime sentAt;
    private OffsetDateTime receivedAt;
    private Integer size;
    private Multipart body;

    public TestMessageBuilder setMessageId(String messageId) {
      this.messageId = messageId;
      return this;
    }

    public TestMessageBuilder setSubject(String subject) {
      this.subject = subject;
      return this;
    }

    public TestMessageBuilder setFrom(String from) {
      this.from = from;
      return this;
    }

    public TestMessageBuilder setTo(List<String> strings) {
      this.strings = strings;
      return this;
    }

    public TestMessageBuilder setCc(List<String> cc) {
      this.cc = cc;
      return this;
    }

    public TestMessageBuilder setSentAt(OffsetDateTime sentAt) {
      this.sentAt = sentAt;
      return this;
    }

    public TestMessageBuilder setReceivedAt(OffsetDateTime receivedAt) {
      this.receivedAt = receivedAt;
      return this;
    }

    public TestMessageBuilder setSize(Integer size) {
      this.size = size;
      return this;
    }

    public TestMessageBuilder setBody(Multipart body) {
      this.body = body;
      return this;
    }

    public TestImapMessage createTestMessage() {
      return new TestImapMessage(
          messageId, subject, from, strings, cc, sentAt, receivedAt, size, body);
    }
  }
}
