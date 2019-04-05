/**
 * Copyright (c) Connexta
 *
 * <p>This is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public
 * License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.ditto.replication.api.data;

import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * ReplicationMetadata holds the product metadata that replication cares about. It stores the
 * original unaltered source metadata as well.
 */
public interface ReplicationMetadata {

  /**
   * This is a string of the source metadata.
   *
   * @return
   */
  Object getRawMetadata();

  /**
   * The date this product was modified
   *
   * @return
   */
  Date getLastModified();

  /**
   * The date this metadata was modified
   *
   * @return
   */
  Date getMetadataLastModified();

  /**
   * Tags associated with this product
   *
   * @return
   */
  Set<String> getTags();

  /**
   * The unique product id
   *
   * @return
   */
  String getId();

  /**
   * List of origin identifiers showing the lineage of this data
   *
   * @return
   */
  List<String> getOrigins();

  /**
   * URI for this product
   *
   * @return
   */
  URI getResourceUri();

  /**
   * Size in bytes of this resource
   *
   * @return
   */
  long getSize();

  /**
   * The mime type of this product
   *
   * @return
   */
  String getMimeType();
}
