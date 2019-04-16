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
package com.connexta.replication.adapters.ddf.adaptor;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.xml.Xpp3Driver;
import java.util.Map;
import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;
import javax.xml.stream.XMLInputFactory;
import org.apache.commons.collections.map.CaseInsensitiveMap;
import org.codice.ddf.spatial.ogc.csw.catalog.common.CswAxisOrder;
import org.codice.ddf.spatial.ogc.csw.catalog.common.CswConstants;
import org.codice.ddf.spatial.ogc.csw.catalog.common.converter.DefaultCswRecordMap;
import org.codice.ditto.replication.api.data.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Converts CSW Record to a Metacard. */
public class CswRecordConverter implements Converter {
  public static final MimeType XML_MIME_TYPE = setXmlMimeType();

  private static final Logger LOGGER =
      LoggerFactory.getLogger(
          org.codice.ddf.spatial.ogc.csw.catalog.converter.CswRecordConverter.class);

  private XStream xstream;

  private static XMLInputFactory factory;

  static {
    factory = XMLInputFactory.newInstance();
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
    factory.setProperty(
        XMLInputFactory.SUPPORT_DTD, Boolean.FALSE); // This disables DTDs entirely for that factory
    factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, Boolean.FALSE);
  }

  public CswRecordConverter() {
    xstream = new XStream(new Xpp3Driver());
    xstream.setClassLoader(this.getClass().getClassLoader());
    xstream.registerConverter(this);
    xstream.alias(CswConstants.CSW_RECORD_LOCAL_NAME, Metadata.class);
    xstream.alias(CswConstants.CSW_RECORD, Metadata.class);
  }

  @Override
  public boolean canConvert(Class clazz) {
    return Metadata.class.isAssignableFrom(clazz);
  }

  @Override
  public void marshal(
      Object o,
      HierarchicalStreamWriter hierarchicalStreamWriter,
      MarshallingContext marshallingContext) {}

  @Override
  public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
    Map<String, String> cswAttrMap =
        new CaseInsensitiveMap(
            DefaultCswRecordMap.getDefaultCswRecordMap().getCswToMetacardAttributeNames());
    Object mappingObj = context.get(CswConstants.CSW_MAPPING);

    if (mappingObj instanceof Map<?, ?>) {
      CswUnmarshallHelper.removeExistingAttributes(cswAttrMap, (Map<String, String>) mappingObj);
    }

    CswAxisOrder cswAxisOrder = CswAxisOrder.LON_LAT;
    Object cswAxisOrderObject = context.get(CswConstants.AXIS_ORDER_PROPERTY);

    if (cswAxisOrderObject != null && cswAxisOrderObject.getClass().isEnum()) {
      Enum value = (Enum) cswAxisOrderObject;
      cswAxisOrder = CswAxisOrder.valueOf(value.name());
    }

    Map<String, String> namespaceMap = null;
    Object namespaceObj = context.get(CswConstants.NAMESPACE_DECLARATIONS);

    if (namespaceObj instanceof Map<?, ?>) {
      namespaceMap = (Map<String, String>) namespaceObj;
    }

    Metadata metacard =
        CswUnmarshallHelper.createMetadataFromCswRecord(reader, cswAxisOrder, namespaceMap);

    return metacard;
  }

  private static MimeType setXmlMimeType() {
    try {
      return new MimeType(com.google.common.net.MediaType.APPLICATION_XML_UTF_8.toString());
    } catch (MimeTypeParseException e) {
      return new MimeType();
    }
  }
}
