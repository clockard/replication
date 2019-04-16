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

import com.connexta.replication.data.MetadataImpl;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.Map;
import org.apache.commons.lang.StringUtils;
import org.codice.ddf.spatial.ogc.csw.catalog.common.CswConstants;
import org.codice.ddf.spatial.ogc.csw.catalog.converter.XStreamAttributeCopier;
import org.codice.ditto.replication.api.data.Metadata;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Converts CSW Record to a Metacard. */
public class CswRecordConverter implements Converter {

  private static final Logger LOGGER = LoggerFactory.getLogger(CswRecordConverter.class);

  public CswRecordConverter() {}

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

    Map<String, String> namespaceMap = null;
    Object namespaceObj = context.get(CswConstants.NAMESPACE_DECLARATIONS);

    if (namespaceObj instanceof Map<?, ?>) {
      namespaceMap = (Map<String, String>) namespaceObj;
    }

    Metadata metacard = createMetadataFromCswRecord(reader, namespaceMap);

    return metacard;
  }

  public static Date convertToDate(String value) {
    // Dates are strings and expected to be in ISO8601 format, YYYY-MM-DD'T'hh:mm:ss.sss,
    // per annotations in the CSW Record schema. At least the date portion must be present;
    // the time zone and time are optional.
    try {
      return ISODateTimeFormat.dateOptionalTimeParser().parseDateTime(value).toDate();
    } catch (IllegalArgumentException e) {
      LOGGER.debug("Failed to convert to date {} from ISO Format: {}", value, e);
    }

    // try from java date serialization for the default locale
    try {
      return DateFormat.getDateInstance().parse(value);
    } catch (ParseException e) {
      LOGGER.debug("Unable to convert date {} from default locale format {} ", value, e);
    }

    // default to current date
    LOGGER.debug("Unable to convert {} to a date object, defaulting to current time", value);
    return new Date();
  }

  public static MetadataImpl createMetadataFromCswRecord(
      HierarchicalStreamReader hreader, Map<String, String> namespaceMap) {

    StringWriter metadataWriter = new StringWriter();
    HierarchicalStreamReader reader =
        XStreamAttributeCopier.copyXml(hreader, metadataWriter, namespaceMap);

    Date modified = null;

    String id = reader.getAttribute("gml:id");

    while (reader.hasMoreChildren()) {
      reader.moveDown();

      String attributeName = reader.getAttribute("name");
      if (!reader.hasMoreChildren()) {
        reader.moveUp();
        continue;
      }
      reader.moveDown();
      String value = reader.getValue();
      reader.moveUp();
      LOGGER.debug("attribute name: {} value: {}.", attributeName, value);
      if (StringUtils.isNotEmpty(attributeName) && StringUtils.isNotEmpty(value)) {
        if (attributeName.equals("metacard.modified")) {
          modified = convertToDate(value);
        }
      }
      /* Set Content Type for backwards compatibility */

      reader.moveUp();
    }

    /* Save entire CSW Record XML as the metacard's metadata string */
    return new MetadataImpl(metadataWriter.toString(), Metadata.class, id, modified);
  }
}
