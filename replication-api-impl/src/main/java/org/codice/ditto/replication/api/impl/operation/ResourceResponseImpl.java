package org.codice.ditto.replication.api.impl.operation;

import java.util.Collection;
import org.codice.ditto.replication.api.operation.Resource;
import org.codice.ditto.replication.api.operation.ResourceResponse;

public class ResourceResponseImpl implements ResourceResponse {

  private Resource resource;

  private Collection<String> errors;

  public ResourceResponseImpl(Resource resource, Collection<String> errors) {
    this.resource = resource;
    this.errors = errors;
  }

  @Override
  public Resource getResource() {
    return resource;
  }

  @Override
  public Collection<String> getErrors() {
    return errors;
  }
}
