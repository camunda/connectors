/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.email.core.jakarta;

import jakarta.mail.*;

public class TestStore extends Store {
  /**
   * Constructor.
   *
   * @param session Session object for this Store.
   * @param urlname URLName object to be used for this Store
   */
  protected TestStore(Session session, URLName urlname) {
    super(session, urlname);
  }

  @Override
  public Folder getDefaultFolder() throws MessagingException {
    return null;
  }

  @Override
  public Folder getFolder(String name) throws MessagingException {
    return null;
  }

  @Override
  public Folder getFolder(URLName url) throws MessagingException {
    return null;
  }
}
