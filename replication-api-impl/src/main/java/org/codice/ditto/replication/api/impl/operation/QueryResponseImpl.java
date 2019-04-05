package org.codice.ditto.replication.api.impl.operation;

import java.util.ArrayList;
import java.util.Collection;
import org.codice.ditto.replication.api.data.ReplicationMetadata;
import org.codice.ditto.replication.api.operation.QueryResponse;

public class QueryResponseImpl implements QueryResponse {

  private long hits;

  private Collection<ReplicationMetadata> results;

  @Override
  public long getTotalHits() {
    return hits;
  }

  @Override
  public Collection<ReplicationMetadata> getResults() {
    return results;
  }

  public void setTotalHits(long hits) {
    this.hits = hits;
  }

  public void setResults(Collection<ReplicationMetadata> data) {
    this.results = new ArrayList<>(data);
  }
}
