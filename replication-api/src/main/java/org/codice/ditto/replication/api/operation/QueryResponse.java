/**
 * Copyright (c) Codice Foundation
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
package org.codice.ditto.replication.api.operation;

import java.util.Collection;
import org.codice.ditto.replication.api.data.ReplicationMetadata;

public interface QueryResponse {

  /**
   * The total number of hits matching the associated Query for the associated store, -1 if unknown.
   * This is typically more than the number of results returned from {@link #getResults()}.
   *
   * @return long - total hits matching this {@link Query}, -1 if unknown.
   */
  long getTotalHits();

  /**
   * Get the ReplicationMetadata of the associated Query
   *
   * @return A collection of results
   */
  Collection<ReplicationMetadata> getResults();
}
