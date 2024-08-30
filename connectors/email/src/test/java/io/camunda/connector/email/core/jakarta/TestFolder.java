/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.core.jakarta;

import jakarta.mail.*;

public class TestFolder extends Folder {

  private Message[] messages;

  /**
   * Constructor that takes a Store object.
   *
   * @param store the Store that holds this folder
   */
  protected TestFolder(Store store) {
    super(store);
  }

  public TestFolder(Store store, Message... messages) {
    super(store);
    this.messages = messages;
  }

  @Override
  public String getName() {
    return "";
  }

  @Override
  public String getFullName() {
    return "";
  }

  @Override
  public Folder getParent() throws MessagingException {
    return null;
  }

  @Override
  public boolean exists() throws MessagingException {
    return false;
  }

  @Override
  public Folder[] list(String pattern) throws MessagingException {
    return new Folder[0];
  }

  @Override
  public char getSeparator() throws MessagingException {
    return 0;
  }

  @Override
  public int getType() throws MessagingException {
    return 0;
  }

  @Override
  public boolean create(int type) throws MessagingException {
    return false;
  }

  @Override
  public boolean hasNewMessages() throws MessagingException {
    return false;
  }

  @Override
  public Folder getFolder(String name) throws MessagingException {
    return null;
  }

  @Override
  public boolean delete(boolean recurse) throws MessagingException {
    return false;
  }

  @Override
  public boolean renameTo(Folder f) throws MessagingException {
    return false;
  }

  @Override
  public void open(int mode) throws MessagingException {}

  @Override
  public void close(boolean expunge) throws MessagingException {}

  @Override
  public boolean isOpen() {
    return true;
  }

  @Override
  public Flags getPermanentFlags() {
    return null;
  }

  @Override
  public int getMessageCount() throws MessagingException {
    return this.messages.length;
  }

  @Override
  public Message getMessage(int msgnum) throws MessagingException {
    return this.messages[msgnum - 1];
  }

  @Override
  public void appendMessages(Message[] msgs) throws MessagingException {}

  @Override
  public Message[] expunge() throws MessagingException {
    return new Message[0];
  }
}
