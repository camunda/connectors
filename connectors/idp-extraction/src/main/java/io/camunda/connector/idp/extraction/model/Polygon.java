/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.model;

import java.util.List;

public class Polygon {
  private int page;
  private List<PolygonPoint> points;

  public Polygon(int page, List<PolygonPoint> points) {
    this.page = page;
    this.points = points;
  }

  public int getPage() {
    return page;
  }

  public void setPage(int page) {
    this.page = page;
  }

  public List<PolygonPoint> getPoints() {
    return points;
  }

  public void setPoints(List<PolygonPoint> points) {
    this.points = points;
  }
}
