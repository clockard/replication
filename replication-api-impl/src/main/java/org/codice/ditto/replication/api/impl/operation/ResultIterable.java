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
package org.codice.ditto.replication.api.impl.operation;

import static com.google.common.collect.Iterators.limit;
import static org.apache.commons.lang.Validate.isTrue;
import static org.apache.commons.lang.Validate.notNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import org.codice.ditto.replication.api.data.ReplicationMetadata;
import org.codice.ditto.replication.api.operation.Query;
import org.codice.ditto.replication.api.operation.QueryFunction;
import org.codice.ditto.replication.api.operation.QueryResponse;

/**
 * Class used to iterate over the {@link ReplicationMetadata} objects contained in a {@link
 * ddf.catalog.operation.QueryResponse} returned when executing a {@link Query}. The class will
 * fetch new results as needed until all results that match the query provided have been exhausted.
 *
 * <p>Since the class may use the page size provided in the {@link Query} to fetch the results, its
 * value should be carefully set to avoid any memory or performance issues.
 */
public class ResultIterable implements Iterable<ReplicationMetadata> {
  public static final int DEFAULT_PAGE_SIZE = 64;

  private final QueryFunction queryFunction;

  private final Query queryRequest;

  private final int maxResultCount;

  private ResultIterable(QueryFunction queryFunction, Query queryRequest, int maxResultCount) {
    notNull(queryFunction, "Query function cannot be null");
    notNull(queryRequest, "Query request cannot be null");
    isTrue(maxResultCount >= 0, "Max Results cannot be negative", maxResultCount);

    this.queryFunction = queryFunction;
    this.queryRequest = queryRequest;
    this.maxResultCount = maxResultCount;
  }

  /**
   * Creates an iterable that will call a {@link QueryFunction} to retrieve the results that match
   * the {@link Query} provided. There will be no limit to the number of results returned.
   *
   * @param queryFunction reference to the {@link QueryFunction} to call to retrieve the results.
   * @param queryRequest request used to retrieve the results.
   */
  public static ResultIterable resultIterable(QueryFunction queryFunction, Query queryRequest) {
    return new ResultIterable(queryFunction, queryRequest, 0);
  }

  /**
   * Creates an iterable that will call a {@link QueryFunction} to retrieve the results that match
   * the {@link Query} provided.
   *
   * @param queryFunction reference to the {@link QueryFunction} to call to retrieve the results.
   * @param queryRequest request used to retrieve the results.
   * @param maxResultCount a positive integer indicating the maximum number of results in total to
   *     query for
   */
  public static ResultIterable resultIterable(
      QueryFunction queryFunction, Query queryRequest, int maxResultCount) {
    isTrue(maxResultCount > 0, "Max Results must be a positive integer", maxResultCount);
    return new ResultIterable(queryFunction, queryRequest, maxResultCount);
  }

  private static Stream<ReplicationMetadata> stream(Iterator<ReplicationMetadata> iterator) {
    return StreamSupport.stream(
        Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false);
  }

  @Override
  public Iterator<ReplicationMetadata> iterator() {
    if (maxResultCount > 0) {
      return limit(new ResultIterator(queryFunction, queryRequest), maxResultCount);
    }
    return new ResultIterator(queryFunction, queryRequest);
  }

  public Stream<ReplicationMetadata> stream() {
    return stream(iterator());
  }

  private static class ResultIterator implements Iterator<ReplicationMetadata> {

    private final QueryFunction queryFunction;
    private final Set<String> foundIds = new HashSet<>(2048);
    private int currentIndex;
    private QueryImpl queryCopy;
    private Iterator<ReplicationMetadata> results = Collections.emptyIterator();
    private boolean finished = false;

    ResultIterator(QueryFunction queryFunction, Query queryRequest) {
      this.queryFunction = queryFunction;

      copyQueryRequestAndQuery(queryRequest);

      this.currentIndex = queryCopy.getStartIndex();
    }

    @Override
    public boolean hasNext() {
      if (results.hasNext()) {
        return true;
      }

      if (finished) {
        return false;
      }

      fetchNextResults();

      // Recurse to ensure we continue querying even if we get 1 or more completely filtered pages.
      return hasNext();
    }

    @Override
    public ReplicationMetadata next() {
      if (results.hasNext()) {
        return results.next();
      }

      if (finished) {
        throw new NoSuchElementException("No more results match the specified query");
      }

      fetchNextResults();

      if (!results.hasNext()) {
        throw new NoSuchElementException("No more results match the specified query");
      }

      return results.next();
    }

    @SuppressWarnings("squid:CommentedOutCodeLine")
    private void fetchNextResults() {
      queryCopy.setStartIndex(currentIndex);

      QueryResponse response = queryFunction.query(queryCopy);

      final Collection<ReplicationMetadata> resultList = response.getResults();

      // Because some of the results may be filtered out by the catalog framework's
      // plugins, we need a way to know the actual page size and increment currentIndex based
      // on that number instead of using the result list size.
      // If the property is not present, we will have no option but to fallback to the size
      // of the (potentially filtered) resultList.
      //
      // This means that if the filtered results size is zero, but the raw number of results
      // had been greater than zero, we will not find results beyond the filtered gap. In practice
      // this should not happen, as queries will run through the QueryOperations.query() method;
      // however, should a user ever construct a QueryFunction that does NOT rely on that method,
      // there is no guarantee that this property will be properly set.
      int actualResultSize = resultList.size();
      currentIndex += actualResultSize;

      List<ReplicationMetadata> dedupedResults = new ArrayList<>(resultList.size());
      for (ReplicationMetadata result : resultList) {
        if (isDistinctResult(result)) {
          dedupedResults.add(result);
        }
        Optional.ofNullable(result).map(ReplicationMetadata::getId).ifPresent(foundIds::add);
      }

      this.results = dedupedResults.iterator();

      if (response.getTotalHits() >= 0 && currentIndex > response.getTotalHits()) {
        finished = true;
      }
    }

    private boolean isDistinctResult(@Nullable ReplicationMetadata result) {
      return result != null && (result.getId() == null || !foundIds.contains(result.getId()));
    }

    private void copyQueryRequestAndQuery(Query query) {

      int pageSize = query.getPageSize() > 1 ? query.getPageSize() : DEFAULT_PAGE_SIZE;

      this.queryCopy =
          new QueryImpl(
              query,
              query.getStartIndex(),
              pageSize,
              query.getSortBy(),
              true,
              // always get the hit count
              query.getTimeoutMillis());
    }
  }
}
