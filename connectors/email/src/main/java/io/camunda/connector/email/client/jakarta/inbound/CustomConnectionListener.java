/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.client.jakarta.inbound;

import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import jakarta.mail.event.ConnectionEvent;
import jakarta.mail.event.ConnectionListener;
import org.eclipse.angus.mail.imap.IMAPFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomConnectionListener implements ConnectionListener {

  private static final Logger log = LoggerFactory.getLogger(CustomConnectionListener.class);
  private final IMAPFolder folder;
  private final Runnable runnable;

  public CustomConnectionListener(IMAPFolder folder, Runnable runnable) {
    this.folder = folder;
    this.runnable = runnable;
  }

  public static CustomConnectionListener create(IMAPFolder folder, Runnable r) {
    return new CustomConnectionListener(folder, r);
  }

  @Override
  public void opened(ConnectionEvent e) {
    log.debug("The folder is successfully opened.");
    log.debug("Starting the email consumer...");
    this.runnable.run();
  }

  @Override
  public void disconnected(ConnectionEvent e) {}

  @Override
  public void closed(ConnectionEvent e) {
    log.debug("The folder has been closed. Reopening it...");
    try {
      this.folder.open(Folder.READ_WRITE);
    } catch (MessagingException ex) {
      throw new RuntimeException(ex);
    }
  }
}
