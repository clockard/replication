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
package org.codice.ditto.replication.api.impl;

import ddf.catalog.core.versioning.MetacardVersion;
import ddf.catalog.data.types.Core;
import ddf.catalog.filter.FilterBuilder;
import ddf.catalog.filter.impl.SortByImpl;
import ddf.catalog.source.SourceUnavailableException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.apache.commons.collections4.CollectionUtils;
import org.codice.ditto.replication.api.ReplicationException;
import org.codice.ditto.replication.api.ReplicationItem;
import org.codice.ditto.replication.api.ReplicationPersistentStore;
import org.codice.ditto.replication.api.ReplicationStatus;
import org.codice.ditto.replication.api.ReplicationStore;
import org.codice.ditto.replication.api.ReplicatorHistory;
import org.codice.ditto.replication.api.Status;
import org.codice.ditto.replication.api.data.ReplicationMetadata;
import org.codice.ditto.replication.api.data.ReplicatorConfig;
import org.codice.ditto.replication.api.impl.operation.QueryImpl;
import org.codice.ditto.replication.api.impl.operation.ResourceImpl;
import org.codice.ditto.replication.api.impl.operation.ResultIterable;
import org.codice.ditto.replication.api.mcard.Replication;
import org.codice.ditto.replication.api.operation.Query;
import org.codice.ditto.replication.api.operation.ResourceResponse;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
import org.opengis.filter.Filter;
import org.opengis.filter.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SyncHelper {

  private static final Logger LOGGER = LoggerFactory.getLogger(SyncHelper.class);

  private final ReplicationStore source;

  private final String sourceName;

  private final ReplicationStore destination;

  private final String destinationName;

  private final ReplicatorConfig config;

  private final ReplicationPersistentStore persistentStore;

  private final ReplicatorHistory history;

  private final FilterBuilder builder;

  private ReplicationMetadata mcard;

  private Optional<ReplicationItem> existingReplicationItem;

  private long syncCount;

  private long failCount;

  private long bytesTransferred;

  private boolean canceled = false;

  private ReplicationStatus status;

  public SyncHelper(
      ReplicationStore source,
      ReplicationStore destination,
      ReplicatorConfig config,
      ReplicationStatus status,
      ReplicationPersistentStore persistentStore,
      ReplicatorHistory history,
      FilterBuilder builder) {
    this.source = source;
    this.destination = destination;
    this.config = config;
    this.status = status;
    this.persistentStore = persistentStore;
    this.history = history;
    this.builder = builder;
    this.sourceName = source.getSystemName();
    this.destinationName = destination.getSystemName();
    syncCount = 0;
    failCount = 0;
    bytesTransferred = 0;
  }

  @SuppressWarnings("squid:S3655" /*isUpdatable performs the needed optional check*/)
  public SyncResponse sync() {
    for (ReplicationMetadata metadata : getMetacardChangeSet()) {
      if (canceled) {
        break;
      }
      mcard = metadata;
      existingReplicationItem = persistentStore.getItem(mcard.getId(), sourceName, destinationName);

      try {
        if (isDeletedMetacard()) {
          processDeletedMetacard();
        } else if (isUpdatable()) {
          processUpdate(existingReplicationItem.get());
        } else {
          processCreate();
        }
      } catch (Exception e) {
        if (causedByConnectionLoss(e)) {
          logConnectionLoss();
          status.setStatus(Status.CONNECTION_LOST);
          return new SyncResponse(syncCount, failCount, bytesTransferred, status.getStatus());
        } else {
          recordItemFailure(e);
        }
      }
    }
    status.setStatus(canceled ? Status.CANCELED : Status.SUCCESS);
    return new SyncResponse(syncCount, failCount, bytesTransferred, status.getStatus());
  }

  /**
   * Cancels the currently running sync job. It will wait till the current processing item is
   * finished.
   */
  public void cancel() {
    this.canceled = true;
  }

  public boolean isCanceled() {
    return this.canceled;
  }

  private Iterable<ReplicationMetadata> getMetacardChangeSet() {
    Filter filter = buildFilter();

    final Query request =
        new QueryImpl(
            filter, 1, 100, new SortByImpl(Core.METACARD_MODIFIED, SortOrder.ASCENDING), false, 0L);
    return ResultIterable.resultIterable(source::query, request);
  }

  private Filter buildFilter() {
    final Filter ecqlFilter;
    final List<Filter> filters = createBasicMetacardFilters();
    final List<Filter> failureFilters = createFailedMetacardFilters();
    final ReplicationStatus lastSuccessfulRun =
        history
            .getReplicationEvents(config.getName())
            .stream()
            .filter(s -> s.getStatus().equals(Status.SUCCESS))
            .findFirst()
            .orElse(null);
    Filter finalFilter;

    try {
      ecqlFilter = ECQL.toFilter(config.getFilter());
    } catch (CQLException e) {
      throw new ReplicationException("Error creating filter from cql: " + config.getFilter(), e);
    }

    if (lastSuccessfulRun != null) {
      long time = lastSuccessfulRun.getStartTime().getTime();
      if (lastSuccessfulRun.getLastSuccess() != null) {
        time = lastSuccessfulRun.getLastSuccess().getTime();
      }
      Date timeStamp = new Date(time - 1000);
      List<Filter> deletedFilters = createDeletedMetacardFilters(timeStamp);
      filters.add(builder.attribute(Core.METACARD_MODIFIED).is().after().date(timeStamp));
      finalFilter =
          builder.allOf(
              ecqlFilter, builder.anyOf(builder.allOf(filters), builder.allOf(deletedFilters)));
    } else {
      filters.add(ecqlFilter);
      finalFilter = builder.allOf(filters);
    }

    if (!failureFilters.isEmpty()) {
      finalFilter = builder.anyOf(finalFilter, builder.anyOf(failureFilters));
    }
    return finalFilter;
  }

  private List<Filter> createBasicMetacardFilters() {
    final List<Filter> filters = new ArrayList<>();
    filters.add(
        builder.not(builder.attribute(Replication.ORIGINS).is().equalTo().text(destinationName)));
    return filters;
  }

  private List<Filter> createFailedMetacardFilters() {
    final List<Filter> failureFilters = new ArrayList<>();
    List<String> failedIDs =
        persistentStore.getFailureList(config.getFailureRetryCount(), sourceName, destinationName);
    for (String id : failedIDs) {
      failureFilters.add(builder.attribute(Core.ID).is().equalTo().text(id));
    }
    return failureFilters;
  }

  private List<Filter> createDeletedMetacardFilters(Date timeStamp) {
    final List<Filter> deletedFilters = new ArrayList<>();
    deletedFilters.add(builder.attribute(MetacardVersion.VERSIONED_ON).after().date(timeStamp));
    deletedFilters.add(
        builder.attribute(Core.METACARD_TAGS).is().equalTo().text(MetacardVersion.VERSION_TAG));
    deletedFilters.add(builder.attribute(MetacardVersion.ACTION).is().like().text("Deleted*"));
    return deletedFilters;
  }

  private boolean isDeletedMetacard() {
    return mcard.getTags().contains("revision");
  }

  private void processDeletedMetacard() {
    String mcardId = mcard.getId();
    existingReplicationItem = persistentStore.getItem(mcardId, sourceName, destinationName);

    if (existingReplicationItem.isPresent()) {

      final ResourceResponse deleteResponse = destination.delete(new ResourceImpl(mcard));
      checkForProcessingErrors(deleteResponse, "DeleteRequest");

      // remove oldReplicationItem from the store if the deleteRequest is successful
      persistentStore.deleteItem(mcardId, sourceName, destinationName);
      syncCount++;
      status.incrementCount();
    } else {
      LOGGER.trace(
          "No replication item for deleted metacard (id = {}). Not sending a delete request.",
          mcardId);
    }
  }

  private boolean isUpdatable() {
    return existingReplicationItem.isPresent() && metacardExists(mcard.getId(), destination);
  }

  private boolean metacardExists(String id, ReplicationStore store) {
    try {
      return store
              .query(new QueryImpl(builder.attribute(Core.ID).is().equalTo().text(id)))
              .getTotalHits()
          > 0;
    } catch (UnsupportedOperationException e) {
      throw new ReplicationException(
          "Error checking for the existence of metacard " + id + " on " + store.getSystemName());
    }
  }

  private void processUpdate(ReplicationItem replicationItem) {
    prepMetacard();
    if (resourceShouldBeUpdated(replicationItem)) {
      performResourceUpdate();
    } else if (metacardShouldBeUpdated(replicationItem)) {
      performMetacardUpdate();
    } else {
      logMetacardSkipped();
    }
  }

  private boolean resourceShouldBeUpdated(ReplicationItem replicationItem) {
    boolean hasResource = mcard.getResourceUri() != null;
    Date resourceModified = mcard.getLastModified();
    return hasResource
        && (resourceModified.after(replicationItem.getResourceModified())
            || replicationItem.getFailureCount() > 0);
  }

  private void performResourceUpdate() {
    final ResourceResponse updateResponse = destination.update(new ResourceImpl(mcard));
    checkForProcessingErrors(updateResponse, "UpdateStorageRequest");
    long bytes = mcard.getSize();
    bytesTransferred += bytes;
    recordSuccessfulReplication();
    status.incrementBytesTransferred(bytes);
  }

  private boolean metacardShouldBeUpdated(ReplicationItem replicationItem) {
    Date metacardModified = mcard.getMetadataLastModified();
    return metacardModified.after(replicationItem.getMetacardModified())
        || replicationItem.getFailureCount() > 0;
  }

  private void performMetacardUpdate() {
    final ResourceResponse updateResponse = destination.update(new ResourceImpl(mcard));
    checkForProcessingErrors(updateResponse, "UpdateRequest");
    recordSuccessfulReplication();
  }

  private void logMetacardSkipped() {
    LOGGER.trace(
        "Not updating product (id = {}, hasResource = {}, resource modified = {}, existing replication item: {})",
        mcard.getId(),
        mcard.getResourceUri() != null,
        mcard.getLastModified(),
        existingReplicationItem);
  }

  private void processCreate() {
    prepMetacard();

    performResourceCreate();

    recordSuccessfulReplication();
  }

  private void performResourceCreate() {
    final ResourceResponse createResponse = destination.create(new ResourceImpl(mcard));
    checkForProcessingErrors(createResponse, "CreateStorageRequest");
    long bytes = mcard.getSize();
    bytesTransferred += bytes;
    status.incrementBytesTransferred(bytes);
  }

  private void prepMetacard() {

    mcard.getOrigins().add(source.getSystemName());
    mcard.getTags().add(Replication.REPLICATED_TAG);
  }

  private void recordSuccessfulReplication() {
    persistentStore.saveItem(createReplicationItem());
    syncCount++;
    status.incrementCount();
  }

  private void checkForProcessingErrors(ResourceResponse response, String requestType) {
    if (CollectionUtils.isNotEmpty(response.getErrors())) {
      throw new ReplicationException("Processing errors when submitting a " + requestType);
    }
  }

  private boolean causedByConnectionLoss(Exception e) {
    return e instanceof SourceUnavailableException
        || !source.isAvailable()
        || !destination.isAvailable();
  }

  private void logConnectionLoss() {
    LOGGER.debug(
        "Encountered connection loss with source {}. Not continuing to process. Marking status as {}.",
        sourceName,
        Status.CONNECTION_LOST);
  }

  private void recordItemFailure(Exception e) {
    ReplicationItem newReplicationItem;

    LOGGER.debug("Exception processing record for metacard id {}", mcard.getId(), e);
    if (existingReplicationItem.isPresent()) {
      newReplicationItem = existingReplicationItem.get();
    } else {
      newReplicationItem = createReplicationItem();
    }
    newReplicationItem.incrementFailureCount();
    persistentStore.saveItem(newReplicationItem);
    failCount++;
    status.incrementFailure();
  }

  private ReplicationItem createReplicationItem() {
    String mcardId = mcard.getId();
    Date resourceModified = mcard.getLastModified();
    Date metacardModified = mcard.getMetadataLastModified();

    return new ReplicationItemImpl(
        mcardId,
        resourceModified,
        metacardModified,
        sourceName,
        destinationName,
        existingReplicationItem.isPresent()
            ? existingReplicationItem.get().getConfigurationId()
            : config.getId());
  }
}
