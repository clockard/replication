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
package org.codice.ditto.replication.api.impl.data;

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.codice.ditto.replication.api.data.ReplicationMetadata;

public class MetadataImpl implements ReplicationMetadata {

  private Object metadata;
  private Date lastModified;
  private Date metadataLastModified;
  private Set<String> tags;
  private String id;
  private List<String> origins;
  private URI uri;
  private long size;
  private String mimeType;

  @Override
  public Object getRawMetadata() {
    return metadata;
  }

  @Override
  public Date getLastModified() {
    return lastModified;
  }

  @Override
  public Date getMetadataLastModified() {
    return metadataLastModified;
  }

  @Override
  public Set<String> getTags() {
    return tags;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public List<String> getOrigins() {
    return origins;
  }

  @Override
  public URI getResourceUri() {
    return uri;
  }

  @Override
  public long getSize() {
    return size;
  }

  @Override
  public String getMimeType() {
    return mimeType;
  }

  public MetadataImpl metadata(Object metadata) {
    this.metadata = metadata;
    return this;
  }

  public MetadataImpl lastModified(Date lastModified) {
    this.lastModified = lastModified;
    return this;
  }

  public MetadataImpl metadataLastModified(Date metadataLastModified) {
    this.metadataLastModified = metadataLastModified;
    return this;
  }

  public MetadataImpl tags(Set<String> tags) {
    this.tags = new HashSet(tags);
    return this;
  }

  public MetadataImpl id(String id) {
    this.id = id;
    return this;
  }

  public MetadataImpl origins(List<String> origins) {
    this.origins = new ArrayList(origins);
    return this;
  }

  public MetadataImpl uri(URI uri) {
    this.uri = uri;
    return this;
  }

  public MetadataImpl size(long size) {
    this.size = size;
    return this;
  }

  public MetadataImpl mimeType(String mimeType) {
    this.mimeType = mimeType;
    return this;
  }
}
