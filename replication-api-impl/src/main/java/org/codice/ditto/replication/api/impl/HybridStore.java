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

import com.google.common.base.Splitter;
import com.google.common.io.ByteSource;
import com.thoughtworks.xstream.converters.Converter;
import ddf.catalog.Constants;
import ddf.catalog.content.data.ContentItem;
import ddf.catalog.content.data.impl.ContentItemImpl;
import ddf.catalog.content.operation.CreateStorageRequest;
import ddf.catalog.content.operation.UpdateStorageRequest;
import ddf.catalog.content.operation.impl.CreateStorageRequestImpl;
import ddf.catalog.content.operation.impl.UpdateStorageRequestImpl;
import ddf.catalog.data.BinaryContent;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.Result;
import ddf.catalog.data.impl.AttributeImpl;
import ddf.catalog.data.types.Core;
import ddf.catalog.data.types.Media;
import ddf.catalog.operation.CreateResponse;
import ddf.catalog.operation.DeleteRequest;
import ddf.catalog.operation.DeleteResponse;
import ddf.catalog.operation.OperationTransaction.OperationType;
import ddf.catalog.operation.ProcessingDetails;
import ddf.catalog.operation.Query;
import ddf.catalog.operation.QueryRequest;
import ddf.catalog.operation.ResourceResponse;
import ddf.catalog.operation.SourceResponse;
import ddf.catalog.operation.UpdateRequest;
import ddf.catalog.operation.UpdateResponse;
import ddf.catalog.operation.impl.CreateRequestImpl;
import ddf.catalog.operation.impl.CreateResponseImpl;
import ddf.catalog.operation.impl.DeleteRequestImpl;
import ddf.catalog.operation.impl.OperationTransactionImpl;
import ddf.catalog.operation.impl.ProcessingDetailsImpl;
import ddf.catalog.operation.impl.QueryImpl;
import ddf.catalog.operation.impl.QueryRequestImpl;
import ddf.catalog.operation.impl.ResourceResponseImpl;
import ddf.catalog.operation.impl.UpdateRequestImpl;
import ddf.catalog.operation.impl.UpdateResponseImpl;
import ddf.catalog.resource.Resource;
import ddf.catalog.resource.ResourceNotFoundException;
import ddf.catalog.resource.impl.ResourceImpl;
import ddf.catalog.source.IngestException;
import ddf.catalog.source.UnsupportedQueryException;
import ddf.catalog.transform.CatalogTransformerException;
import ddf.catalog.transform.MetacardTransformer;
import ddf.security.SecurityConstants;
import ddf.security.SubjectUtils;
import ddf.security.encryption.EncryptionService;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import net.jodah.failsafe.util.concurrent.DefaultScheduledFuture;
import org.apache.commons.lang.StringUtils;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.ext.multipart.Attachment;
import org.apache.cxf.jaxrs.ext.multipart.ContentDisposition;
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;
import org.apache.cxf.jaxrs.impl.MetadataMap;
import org.apache.shiro.SecurityUtils;
import org.codice.ddf.cxf.client.ClientFactoryFactory;
import org.codice.ddf.cxf.client.SecureCxfClientFactory;
import org.codice.ddf.endpoints.rest.RESTService;
import org.codice.ddf.security.common.Security;
import org.codice.ddf.spatial.ogc.catalog.common.AvailabilityTask;
import org.codice.ddf.spatial.ogc.csw.catalog.common.CswSourceConfiguration;
import org.codice.ddf.spatial.ogc.csw.catalog.common.source.AbstractCswStore;
import org.codice.ditto.replication.api.ReplicationException;
import org.codice.ditto.replication.api.ReplicationStore;
import org.codice.ditto.replication.api.data.ReplicationMetadata;
import org.codice.ditto.replication.api.impl.data.MetadataImpl;
import org.codice.ditto.replication.api.impl.operation.QueryResponseImpl;
import org.codice.ditto.replication.api.mcard.Replication;
import org.codice.ditto.replication.api.operation.QueryResponse;
import org.opengis.filter.Filter;
import org.osgi.framework.BundleContext;

public class HybridStore extends AbstractCswStore implements ReplicationStore {

  public static final String REGISTRY_TAG = "registry";

  private static final String REGISTRY_IDENTITY_NODE = "registry.local.registry-identity-node";

  private static final String CONTENT_DISPOSITION = "Content-Disposition";

  private SecureCxfClientFactory<RESTService> restClientFactory;

  private String remoteName;

  public HybridStore(
      BundleContext context,
      CswSourceConfiguration cswSourceConfiguration,
      Converter provider,
      ClientFactoryFactory clientFactoryFactory,
      EncryptionService encryptionService,
      URL url) {
    super(context, cswSourceConfiguration, provider, clientFactoryFactory, encryptionService);
    this.restClientFactory =
        clientFactoryFactory.getSecureCxfClientFactory(
            url.toString() + "/services/catalog", RESTService.class);
  }

  @Override
  public void init() {
    super.init();
    configureCswSource();
  }

  @Override
  protected void setupAvailabilityPoll() {
    availabilityPollFuture = new DefaultScheduledFuture();
    setAvailabilityTask(new AvailabilityTask(0, null, null));
  }

  @Override
  protected void loadContentTypes() {
    // do nothing
  }

  @Override
  public ResourceResponse retrieveResource(
      URI resourceUri, Map<String, Serializable> requestProperties)
      throws ResourceNotFoundException {

    Serializable serializableId = null;
    String username = cswSourceConfiguration.getUsername();
    String password = cswSourceConfiguration.getPassword();

    if (requestProperties != null) {
      serializableId = requestProperties.get(Core.ID);
      if (StringUtils.isNotBlank(username)) {
        requestProperties.put(USERNAME_PROPERTY, username);
        requestProperties.put(PASSWORD_PROPERTY, password);
      }
    }
    WebClient client =
        restClientFactory
            .getWebClientForSubject(
                (ddf.security.Subject)
                    Objects.requireNonNull(requestProperties)
                        .get(SecurityConstants.SECURITY_SUBJECT))
            .path("sources/{sourceid}/{mcardid}", remoteName, serializableId)
            .query("transform", "resource");
    String qualifier = getQualifier(resourceUri);
    if (qualifier != null) {
      client = client.query("qualifier", qualifier);
    }

    Response response = client.get();
    if (!response.getStatusInfo().getFamily().equals(Response.Status.Family.SUCCESSFUL)) {
      throw new ResourceNotFoundException(
          "Error retrieving resource. Code " + response.getStatus());
    }

    String filename = null;
    if (response.getHeaderString(CONTENT_DISPOSITION) != null) {
      filename =
          new ContentDisposition(response.getHeaderString(CONTENT_DISPOSITION)).getFilename();
    }
    Resource resource =
        new ResourceImpl(
            (InputStream) response.getEntity(), response.getMediaType().toString(), filename);
    return new ResourceResponseImpl(resource);
  }

  private String getQualifier(URI uri) {
    String qualifier = uri.getFragment();
    if (qualifier == null && uri.getQuery() != null) {
      Map<String, String> map =
          Splitter.on('&').trimResults().withKeyValueSeparator("=").split(uri.getQuery());
      qualifier = map.get("qualifier");
    }
    return qualifier;
  }

  public CreateResponse create(CreateStorageRequest createStorageRequest) throws IngestException {
    List<Metacard> createdMetacards = new ArrayList<>();
    List<Metacard> originalMetacards = new ArrayList<>();
    HashSet<ProcessingDetails> exceptions = new HashSet<>();
    for (ContentItem item : createStorageRequest.getContentItems()) {
      originalMetacards.add(item.getMetacard());
      Response response = performCreate(item);
      if (response.getStatusInfo().getFamily().equals(Response.Status.Family.SUCCESSFUL)) {
        createdMetacards.add(item.getMetacard());
      } else {
        exceptions.add(
            new ProcessingDetailsImpl(
                remoteName, null, "Error performing remote storage create request."));
      }
    }
    return new CreateResponseImpl(
        new CreateRequestImpl(originalMetacards),
        createStorageRequest.getProperties(),
        createdMetacards,
        exceptions);
  }

  public UpdateResponse update(UpdateStorageRequest updateStorageRequest) throws IngestException {
    List<Metacard> updatedMetacards = new ArrayList<>();
    List<Metacard> originalMetacards = new ArrayList<>();
    HashSet<ProcessingDetails> exceptions = new HashSet<>();
    for (ContentItem item : updateStorageRequest.getContentItems()) {
      originalMetacards.add(item.getMetacard());
      Response response = performUpdate(item);
      if (response.getStatusInfo().getFamily().equals(Response.Status.Family.SUCCESSFUL)) {
        updatedMetacards.add(item.getMetacard());
      } else {
        exceptions.add(
            new ProcessingDetailsImpl(
                remoteName, null, "Error performing remote storage update request."));
      }
    }
    String[] originalMetacardIds =
        originalMetacards.stream().map(Metacard::getId).toArray(String[]::new);
    return new UpdateResponseImpl(
        new UpdateRequestImpl(originalMetacardIds, originalMetacards),
        updateStorageRequest.getProperties(),
        updatedMetacards,
        originalMetacards,
        exceptions);
  }

  @Override
  protected Map<String, Consumer<Object>> getAdditionalConsumers() {
    return new HashMap<>();
  }

  @Override
  public String getSystemName() {
    if (remoteName != null) {
      return remoteName;
    }
    Filter filter =
        filterBuilder.allOf(
            filterBuilder.attribute(Metacard.TAGS).is().equalTo().text(REGISTRY_TAG),
            filterBuilder.not(filterBuilder.attribute(REGISTRY_IDENTITY_NODE).empty()));

    Map<String, Serializable> queryProps = new HashMap<>();
    Query newQuery = new QueryImpl(filter);
    QueryRequest queryRequest = new QueryRequestImpl(newQuery, queryProps);

    SourceResponse identityMetacard;
    try {
      identityMetacard = query(queryRequest);
    } catch (UnsupportedQueryException e) {
      throw new ReplicationException("Could not get remote name from registry metacard", e);
    }
    if (!identityMetacard.getResults().isEmpty()) {
      remoteName = identityMetacard.getResults().get(0).getMetacard().getTitle();
    } else {
      throw new ReplicationException(
          "No registry metacard available on remote node. Could not retrieve remote system name");
    }
    return remoteName;
  }

  private Response performCreate(ContentItem item) throws IngestException {
    WebClient client = setupClient();
    Response response;
    try {
      MultipartBody multipartBody = createBody(item);
      response = client.post(multipartBody);
    } catch (IOException e) {
      throw new IngestException(
          String.format("Could not create attachment for %s", item.getFilename()), e);
    }
    return response;
  }

  private Response performUpdate(ContentItem item) throws IngestException {
    WebClient client = setupClient();
    Response response;
    try {
      String metacardId = item.getMetacard().getId();
      client.path(metacardId);
      MultipartBody multipartBody = createBody(item);
      response = client.post(multipartBody);
    } catch (IOException e) {
      throw new IngestException(
          String.format("Could not create attachment for %s", item.getFilename()), e);
    }
    return response;
  }

  private ContentDisposition createResourceContentDisposition(String resourceFileName) {
    String contentDisposition =
        String.format("form-data; name=parse.resource; filename=%s", resourceFileName);
    return new ContentDisposition(contentDisposition);
  }

  private Attachment createParseResourceAttachment(ContentItem item) throws IOException {
    ContentDisposition contentDisposition = createResourceContentDisposition(item.getFilename());

    MultivaluedMap<String, String> headers = new MetadataMap<>(false, true);
    headers.putSingle(CONTENT_DISPOSITION, contentDisposition.toString());
    headers.putSingle("Content-ID", "parse.resource");
    headers.putSingle("Content-Type", item.getMimeTypeRawData());

    return new Attachment(item.getInputStream(), headers);
  }

  private ContentDisposition createMetadataContentDisposition(String metadataFilename) {
    String contentDisposition =
        String.format("form-data; name=parse.metadata; filename=%s", metadataFilename);
    return new ContentDisposition(contentDisposition);
  }

  private Attachment createMetadataAttachment(ContentItem item) {
    ContentDisposition contentDisposition =
        createMetadataContentDisposition(item.getMetacard().getId());
    MetacardTransformer transformer = schemaTransformerManager.getTransformerById("xml");
    BinaryContent serializedMetacard;

    try {
      serializedMetacard = transformer.transform(item.getMetacard(), new HashMap<>());
    } catch (CatalogTransformerException e) {
      throw new ReplicationException("Error transforming metacard into xml", e);
    }
    return new Attachment(
        "parse.metadata", serializedMetacard.getInputStream(), contentDisposition);
  }

  private MultipartBody createBody(ContentItem item) throws IOException {
    List<Attachment> attachments = new ArrayList<>();
    attachments.add(createParseResourceAttachment(item));
    attachments.add(createMetadataAttachment(item));
    return new MultipartBody(attachments);
  }

  private WebClient setupClient() {
    MultivaluedMap<String, String> headers = new MultivaluedHashMap<>();
    headers.add(HttpHeaders.CONTENT_TYPE, MediaType.MULTIPART_FORM_DATA);

    Security security = Security.getInstance();
    WebClient client =
        security.runAsAdmin(
            () ->
                AccessController.doPrivilegedWithCombiner(
                    (PrivilegedAction<WebClient>)
                        () ->
                            restClientFactory.getWebClientForSubject(security.getSystemSubject())));

    client.headers(headers);
    client.accept(MediaType.APPLICATION_JSON);
    return client;
  }

  @Override
  public QueryResponse query(org.codice.ditto.replication.api.operation.Query query) {
    QueryRequest request =
        new QueryRequestImpl(
            new QueryImpl(
                query,
                query.getStartIndex(),
                query.getPageSize(),
                query.getSortBy(),
                query.requestsTotalResultsCount(),
                query.getTimeoutMillis()));
    try {
      SourceResponse sourceResponse = this.query(request);
      QueryResponseImpl response = new QueryResponseImpl();
      response.setTotalHits(sourceResponse.getHits());
      response.setResults(
          sourceResponse
              .getResults()
              .stream()
              .map(Result::getMetacard)
              .map(this::convertMetacardToMetadata)
              .collect(Collectors.toList()));
      return response;
    } catch (UnsupportedQueryException e) {
      throw new ReplicationException(e);
    }
  }

  @Override
  public org.codice.ditto.replication.api.operation.ResourceResponse create(
      org.codice.ditto.replication.api.operation.Resource resource) {

    try {
      Metacard metacard = convertMetadataToMetacard(resource.getMetadata());
      CreateResponse response;
      if (resource.getMetadata().getResourceUri() != null) {
        ContentItem item = getResourceContentForMetacard(metacard, resource);
        CreateStorageRequest request =
            new CreateStorageRequestImpl(Collections.singletonList(item), new HashMap<>());
        response = this.create(request);
      } else {
        response = this.create(new CreateRequestImpl(metacard));
      }
      if (response.getCreatedMetacards().isEmpty()) {
        throw new ReplicationException("Failed to create metacard/resource on remote host");
      }
      return new org.codice.ditto.replication.api.impl.operation.ResourceResponseImpl(
          new org.codice.ditto.replication.api.impl.operation.ResourceImpl(
              convertMetacardToMetadata(response.getCreatedMetacards().get(0))),
          response
              .getProcessingErrors()
              .stream()
              .map(e -> e.getException().toString())
              .collect(Collectors.toList()));
    } catch (IOException | IngestException e) {
      throw new ReplicationException(e);
    }
  }

  @Override
  public org.codice.ditto.replication.api.operation.ResourceResponse update(
      org.codice.ditto.replication.api.operation.Resource resource) {
    try {
      Metacard metacard = convertMetadataToMetacard(resource.getMetadata());
      UpdateResponse response;
      if (resource.getMetadata().getResourceUri() != null) {
        ContentItem item = getResourceContentForMetacard(metacard, resource);
        UpdateStorageRequest request =
            new UpdateStorageRequestImpl(Collections.singletonList(item), new HashMap<>());
        response = this.update(request);
      } else {
        // adding the operation transaction to avoid NPE when forming the response in
        // AbstractCSWStore
        UpdateRequest updateRequest = new UpdateRequestImpl(metacard.getId(), metacard);
        updateRequest
            .getProperties()
            .put(
                Constants.OPERATION_TRANSACTION_KEY,
                new OperationTransactionImpl(
                    OperationType.UPDATE, Collections.singletonList(metacard)));
        response = this.update(updateRequest);
      }
      if (response.getUpdatedMetacards().isEmpty()) {
        throw new ReplicationException("Failed to update metacard/resource on remote host");
      }
      return new org.codice.ditto.replication.api.impl.operation.ResourceResponseImpl(
          new org.codice.ditto.replication.api.impl.operation.ResourceImpl(
              convertMetacardToMetadata(response.getUpdatedMetacards().get(0).getNewMetacard())),
          response
              .getProcessingErrors()
              .stream()
              .map(e -> e.getException().toString())
              .collect(Collectors.toList()));
    } catch (IOException | IngestException e) {
      throw new ReplicationException(e);
    }
  }

  @Override
  public org.codice.ditto.replication.api.operation.ResourceResponse delete(
      org.codice.ditto.replication.api.operation.Resource resource) {
    try {
      Metacard metacard = convertMetadataToMetacard(resource.getMetadata());
      DeleteRequest deleteRequest = new DeleteRequestImpl(resource.getId());
      // adding the operation transaction to avoid NPE when forming the response in AbstractCSWStore
      deleteRequest
          .getProperties()
          .put(
              Constants.OPERATION_TRANSACTION_KEY,
              new OperationTransactionImpl(
                  OperationType.DELETE, Collections.singletonList(metacard)));
      DeleteResponse response = this.delete(deleteRequest);
      if (response.getDeletedMetacards().isEmpty()) {
        throw new ReplicationException("Failed to delete metacard/resource on remote host");
      }
      return new org.codice.ditto.replication.api.impl.operation.ResourceResponseImpl(
          new org.codice.ditto.replication.api.impl.operation.ResourceImpl(
              convertMetacardToMetadata(response.getDeletedMetacards().get(0))),
          response
              .getProcessingErrors()
              .stream()
              .map(e -> e.getException().toString())
              .collect(Collectors.toList()));
    } catch (IngestException e) {
      throw new ReplicationException(e);
    }
  }

  @Override
  public org.codice.ditto.replication.api.operation.Resource retrieveResource(
      ReplicationMetadata metadata) {
    String mcardId = metadata.getId();
    Map<String, Serializable> properties = new HashMap<>();
    properties.put(Core.ID, mcardId);
    try {
      Resource response =
          this.retrieveResource(metadata.getResourceUri(), properties).getResource();
      return new org.codice.ditto.replication.api.impl.operation.ResourceImpl(
          mcardId,
          new ByteSource() {
            @Override
            public InputStream openStream() {
              return response.getInputStream();
            }
          },
          response.getMimeTypeValue(),
          metadata);
    } catch (ResourceNotFoundException e) {
      throw new ReplicationException("Failed to retrieve resource for metacard " + mcardId, e);
    }
  }

  @Override
  public boolean isAvailable() {
    return (getCapabilities() != null);
  }

  @Override
  public boolean isWritable() {
    return true;
  }

  @Override
  public void close() {
    destroy(100);
  }

  private ReplicationMetadata convertMetacardToMetadata(Metacard mcard) {
    MetadataImpl metadata = new MetadataImpl();
    metadata
        .id(mcard.getId())
        .lastModified(mcard.getModifiedDate())
        .metadataLastModified((Date) mcard.getAttribute(Core.METACARD_MODIFIED).getValue())
        .origins(
            mcard
                .getAttribute(Replication.ORIGINS)
                .getValues()
                .stream()
                .map(String.class::cast)
                .collect(Collectors.toList()))
        .tags(mcard.getTags())
        .uri(mcard.getResourceURI())
        .metadata(mcard);
    if (mcard.getResourceSize() != null) {
      metadata.size(Long.parseLong(mcard.getResourceSize()));
    }

    if (mcard.getAttribute(Media.TYPE) != null) {
      metadata.mimeType((String) mcard.getAttribute(Media.TYPE).getValue());
    }

    return metadata;
  }

  private Metacard convertMetadataToMetacard(ReplicationMetadata metadata) {
    Metacard mcard = (Metacard) metadata.getRawMetadata();
    mcard.setAttribute(
        new AttributeImpl(
            Core.METACARD_TAGS, metadata.getTags().stream().collect(Collectors.toList())));
    mcard.setAttribute(
        new AttributeImpl(
            Replication.ORIGINS, metadata.getOrigins().stream().collect(Collectors.toList())));
    mcard.setAttribute(
        new AttributeImpl(
            Metacard.POINT_OF_CONTACT, SubjectUtils.getEmailAddress(SecurityUtils.getSubject())));
    // We are not transferring derived resources at this point so remove the derived uri/urls
    mcard.setAttribute(
        new AttributeImpl(Metacard.DERIVED_RESOURCE_DOWNLOAD_URL, (Serializable) null));
    mcard.setAttribute(new AttributeImpl(Metacard.DERIVED_RESOURCE_URI, (Serializable) null));
    return mcard;
  }

  private ContentItem getResourceContentForMetacard(
      Metacard mcard, org.codice.ditto.replication.api.operation.Resource resource)
      throws IOException {
    URI uri = mcard.getResourceURI();
    ByteSource byteSource =
        new ByteSource() {
          @Override
          public InputStream openStream() {
            return resource.getInputStream();
          }
        };

    String qualifier = getQualifier(uri);

    return new ContentItemImpl(
        mcard.getId(),
        qualifier,
        byteSource,
        resource.getMimeTypeRawData(),
        null,
        resource.getSize(),
        mcard);
  }
}
