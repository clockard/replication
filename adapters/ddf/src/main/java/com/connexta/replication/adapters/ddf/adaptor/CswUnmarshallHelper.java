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
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import java.io.Serializable;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections.map.CaseInsensitiveMap;
import org.apache.commons.lang.StringUtils;
import org.codice.ddf.spatial.ogc.csw.catalog.common.CswAxisOrder;
import org.codice.ddf.spatial.ogc.csw.catalog.common.CswConstants;
import org.codice.ddf.spatial.ogc.csw.catalog.common.converter.DefaultCswRecordMap;
import org.codice.ddf.spatial.ogc.csw.catalog.converter.XStreamAttributeCopier;
import org.codice.ditto.replication.api.data.Metadata;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CswUnmarshallHelper {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(
          org.codice.ddf.spatial.ogc.csw.catalog.converter.CswUnmarshallHelper.class);

  /**
   * The map of metacard attributes that both the basic DDF MetacardTypeImpl and the CSW
   * MetacardType define as attributes. This is used to detect these element tags when unmarshalling
   * XML so that the tag name can be modified with a CSW-unique prefix before attempting to lookup
   * the attribute descriptor corresponding to the tag.
   */
  private static final List<String> CSW_OVERLAPPING_ATTRIBUTE_NAMES = Arrays.asList();

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

  public static void removeExistingAttributes(
      Map<String, String> cswAttrMap, Map<String, String> mappingObj) {
    // If we got mappings passed in, remove the existing mappings for that attribute
    Map<String, String> customMappings = new CaseInsensitiveMap(mappingObj);
    Map<String, String> convertedMappings = new CaseInsensitiveMap();

    for (Map.Entry<String, String> customMapEntry : customMappings.entrySet()) {
      Iterator<Map.Entry<String, String>> existingMapIter = cswAttrMap.entrySet().iterator();

      while (existingMapIter.hasNext()) {
        Map.Entry<String, String> existingMapEntry = existingMapIter.next();
        if (existingMapEntry.getValue().equalsIgnoreCase(customMapEntry.getValue())) {
          existingMapIter.remove();
        }
      }

      String key = convertToCswField(customMapEntry.getKey());
      String value = customMapEntry.getValue();
      LOGGER.debug("Adding key: {} & value: {}", key, value);
      convertedMappings.put(key, value);
    }

    cswAttrMap.putAll(convertedMappings);

    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug("Map contents: {}", Arrays.toString(cswAttrMap.entrySet().toArray()));
    }
  }

  public static String convertToCswField(String name) {

    if (CSW_OVERLAPPING_ATTRIBUTE_NAMES.contains(name)) {
      return CswConstants.CSW_ATTRIBUTE_PREFIX + name;
    }

    return name;
  }

  public static MetadataImpl createMetadataFromCswRecord(
      HierarchicalStreamReader hreader,
      CswAxisOrder cswAxisOrder,
      Map<String, String> namespaceMap) {

    StringWriter metadataWriter = new StringWriter();
    HierarchicalStreamReader reader =
        XStreamAttributeCopier.copyXml(hreader, metadataWriter, namespaceMap);

    Date modified = null;

    String id = null;

    while (reader.hasMoreChildren()) {
      reader.moveDown();

      String nodeName = reader.getNodeName();
      LOGGER.debug("node name: {}.", nodeName);
      String name = getCswAttributeFromAttributeName(nodeName);
      LOGGER.debug("Processing node {}", name);
      String attributeName = reader.getAttribute(name);
      Serializable value = reader.getValue();

        if (isNotEmpty(value)) {
          if (name.equals("metacard.modified")) {
            modified = convertToDate(value.toString());
          } else if (name.equals("id")) {
            id = value.toString();
          }
        }
        /* Set Content Type for backwards compatibility */

      reader.moveUp();
    }

    /* Save entire CSW Record XML as the metacard's metadata string */
    return new MetadataImpl(metadataWriter.toString(), Metadata.class, id, modified);
  }

  private static boolean isNotEmpty(Serializable serializable) {
    if (serializable instanceof String) {
      String compString = (String) serializable;
      if (StringUtils.isNotEmpty(compString.trim())) {
        return true;
      }
    } else if (serializable != null) {
      return true;
    }
    return false;
  }

  /**
   * Converts an attribute name to the csw:Record attribute it corresponds to.
   *
   * @param attributeName the name of the attribute
   * @return the name of the csw:Record attribute that this attribute name corresponds to
   */
  static String getCswAttributeFromAttributeName(String attributeName) {
    // Remove the prefix if it exists
    if (StringUtils.contains(attributeName, CswConstants.NAMESPACE_DELIMITER)) {
      attributeName = StringUtils.split(attributeName, CswConstants.NAMESPACE_DELIMITER)[1];
    }

    // Some attribute names overlap with basic Metacard attribute names,
    // e.g., "title".
    // So if this is one of those attribute names, get the CSW
    // attribute for the name to be looked up.
    return CswUnmarshallHelper.convertToCswField(attributeName);
  }
}
