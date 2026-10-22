/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.box;

import com.box.sdkgen.box.errors.BoxAPIError;
import com.box.sdkgen.box.errors.ResponseInfo;
import com.box.sdkgen.client.BoxClient;
import com.box.sdkgen.managers.folders.GetFolderItemsQueryParams;
import com.box.sdkgen.schemas.items.Items;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.box.model.BoxPath;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class BoxUtil {

  private static final String ROOT_FOLDER_ID = "0";
  private static final long PAGE_SIZE = 1000L;

  public static String getItemId(String path, BoxClient client) {
    return findItemId(path, client)
        .orElseThrow(
            () ->
                new ConnectorException(
                    "ITEM_NOT_FOUND",
                    "Could not find item '"
                        + path
                        + "'. Paths must start with '/' (e.g. /Invoices/2026), or be a Box item ID."));
  }

  public static Optional<String> findItemId(String path, BoxClient client) {
    return findItemId(BoxPath.from(path), client);
  }

  public static Optional<String> findItemId(BoxPath path, BoxClient client) {
    return switch (path) {
      case BoxPath.Root() -> Optional.of(ROOT_FOLDER_ID);
      case BoxPath.Id id -> Optional.of(id.id());
      case BoxPath.Segments segments -> findItemIdInTree(ROOT_FOLDER_ID, segments, client);
    };
  }

  private static Optional<String> findItemIdInTree(
      String folderId, BoxPath.Segments segments, BoxClient client) {
    String segment = segments.segments().getFirst();
    return findChildIdByName(folderId, segment, client)
        .flatMap(
            childId ->
                segments.isPathEnd()
                    ? Optional.of(childId)
                    : findItemIdInTree(childId, segments.withoutFirstSegment(), client));
  }

  private static Optional<String> findChildIdByName(
      String folderId, String name, BoxClient client) {
    return findChildIdByName(
        name,
        marker -> {
          var queryParams = new GetFolderItemsQueryParams();
          queryParams.usemarker = true;
          queryParams.marker = marker;
          queryParams.limit = PAGE_SIZE;
          Items items = client.folders.getFolderItems(folderId, queryParams);
          List<Entry> entries =
              items.getEntries() == null
                  ? List.of()
                  : items.getEntries().stream()
                      .map(item -> new Entry(item.getId(), item.getName()))
                      .toList();
          return new Page(entries, items.getNextMarker());
        });
  }

  static Optional<String> findChildIdByName(String name, Function<String, Page> fetchPage) {
    String marker = null;
    do {
      Page page = fetchPage.apply(marker);
      for (Entry entry : page.entries()) {
        if (name.equals(entry.name())) {
          return Optional.of(entry.id());
        }
      }
      marker = page.nextMarker();
    } while (marker != null && !marker.isEmpty());
    return Optional.empty();
  }

  record Entry(String id, String name) {}

  record Page(List<Entry> entries, String nextMarker) {}

  public static byte[] download(String fileId, BoxClient client) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      client.downloads.downloadFileToOutputStream(fileId, out);
      return out.toByteArray();
    } catch (BoxAPIError e) {
      throw new RuntimeException(
          "Error downloading file: " + fileId, withDiagnostics(e, fileId, client));
    } catch (Throwable e) {
      throw new RuntimeException("Error downloading file: " + fileId, e);
    }
  }

  private static BoxAPIError withDiagnostics(BoxAPIError error, String fileId, BoxClient client) {
    ResponseInfo response = error.getResponseInfo();
    if (response == null || response.getBody() != null || response.getRawBody() != null) {
      return error;
    }
    try {
      client.files.getFileById(fileId);
    } catch (BoxAPIError diagnostic) {
      ResponseInfo info = diagnostic.getResponseInfo();
      return info != null && info.getStatusCode() == response.getStatusCode() ? diagnostic : error;
    } catch (RuntimeException ignored) {
      return error;
    }
    return error;
  }
}
