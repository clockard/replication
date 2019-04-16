package com.connexta.replication.adapters.ddf.adaptor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.MediaType;
import javax.xml.namespace.QName;
import net.opengis.cat.csw.v_2_0_2.AcknowledgementType;
import net.opengis.cat.csw.v_2_0_2.CapabilitiesType;
import net.opengis.cat.csw.v_2_0_2.ElementSetNameType;
import net.opengis.cat.csw.v_2_0_2.ElementSetType;
import net.opengis.cat.csw.v_2_0_2.GetCapabilitiesType;
import net.opengis.cat.csw.v_2_0_2.GetRecordsResponseType;
import net.opengis.cat.csw.v_2_0_2.GetRecordsType;
import net.opengis.cat.csw.v_2_0_2.ObjectFactory;
import net.opengis.cat.csw.v_2_0_2.QueryConstraintType;
import net.opengis.cat.csw.v_2_0_2.QueryType;
import net.opengis.cat.csw.v_2_0_2.ResultType;
import net.opengis.filter.v_1_1_0.PropertyNameType;
import net.opengis.filter.v_1_1_0.SortByType;
import net.opengis.filter.v_1_1_0.SortOrderType;
import net.opengis.filter.v_1_1_0.SortPropertyType;
import org.codice.ddf.cxf.client.SecureCxfClientFactory;
import org.codice.ddf.cxf.client.impl.SecureCxfClientFactoryImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleCsw {

  private static final Logger LOGGER = LoggerFactory.getLogger(SimpleCsw.class);

  private SecureCxfClientFactory<Csw> factory;

  protected CswJAXBElementProvider<GetRecordsType> getRecordsTypeProvider;

  protected List<String> jaxbElementClassNames = new ArrayList<>();
  protected Map<String, String> jaxbElementClassMap = new HashMap<>();

  private String endpointUrl = "https://ditto-1.phx.connexta.com:8993/services/csw";

  private String username = "admin";

  private String password = "admin";

  public SimpleCsw() {
    factory =
        new SecureCxfClientFactoryImpl<>(
            endpointUrl,
            Csw.class,
            initProviders(),
            null,
            true,
            false,
            3000,
            3000,
            username,
            password);
  }

  protected List<Object> initProviders() {
    getRecordsTypeProvider = new CswJAXBElementProvider<>();
    getRecordsTypeProvider.setMarshallAsJaxbElement(true);

    // Adding class names that need to be marshalled/unmarshalled to
    // jaxbElementClassNames list
    jaxbElementClassNames.add(GetRecordsType.class.getName());
    jaxbElementClassNames.add(CapabilitiesType.class.getName());
    jaxbElementClassNames.add(GetCapabilitiesType.class.getName());
    jaxbElementClassNames.add(GetRecordsResponseType.class.getName());
    jaxbElementClassNames.add(AcknowledgementType.class.getName());

    getRecordsTypeProvider.setJaxbElementClassNames(jaxbElementClassNames);

    // Adding map entry of <Class Name>,<Qualified Name> to jaxbElementClassMap
    String expandedName =
        new QName(CswConstants.CSW_OUTPUT_SCHEMA, CswConstants.GET_RECORDS).toString();
    String message = "{} expanded name: {}";
    LOGGER.debug(message, CswConstants.GET_RECORDS, expandedName);
    jaxbElementClassMap.put(GetRecordsType.class.getName(), expandedName);

    String getCapsExpandedName =
        new QName(CswConstants.CSW_OUTPUT_SCHEMA, CswConstants.GET_CAPABILITIES).toString();
    LOGGER.debug(message, CswConstants.GET_CAPABILITIES, expandedName);
    jaxbElementClassMap.put(GetCapabilitiesType.class.getName(), getCapsExpandedName);

    String capsExpandedName =
        new QName(CswConstants.CSW_OUTPUT_SCHEMA, CswConstants.CAPABILITIES).toString();
    LOGGER.debug(message, CswConstants.CAPABILITIES, capsExpandedName);
    jaxbElementClassMap.put(CapabilitiesType.class.getName(), capsExpandedName);

    String caps201ExpandedName =
        new QName("http://www.opengis.net/cat/csw", CswConstants.CAPABILITIES).toString();
    LOGGER.debug(message, CswConstants.CAPABILITIES, caps201ExpandedName);
    jaxbElementClassMap.put(CapabilitiesType.class.getName(), caps201ExpandedName);

    String acknowledgmentName =
        new QName("http://www.opengis.net/cat/csw/2.0.2", "Acknowledgement").toString();
    jaxbElementClassMap.put(AcknowledgementType.class.getName(), acknowledgmentName);
    getRecordsTypeProvider.setJaxbElementClassMap(jaxbElementClassMap);

    GetRecordsMessageBodyReader grmbr = new GetRecordsMessageBodyReader();

    return Arrays.asList(getRecordsTypeProvider, new CswResponseExceptionMapper(), grmbr);
  }

  public boolean isAvailable() {
    Csw csw = factory.getClient();
    GetCapabilitiesType getCapabilitiesType = new GetCapabilitiesType();
    getCapabilitiesType.setService("CSW");
    try {
      CapabilitiesType respons = csw.getCapabilities(getCapabilitiesType);
      LOGGER.info(
          "Successfully contacted CSW server version {} at {}", respons.getVersion(), endpointUrl);
    } catch (Exception e) {
      LOGGER.warn("Error contacting CSW Server at {}", endpointUrl, e);
      return false;
    }
    return true;
  }

  public void query(String cql, long startIndex, long pageSize) throws Exception {
    Csw csw = factory.getClient();

    GetRecordsType getRecordsType = new GetRecordsType();
    getRecordsType.setVersion(CswConstants.VERSION_2_0_2);
    getRecordsType.setService("CSW");
    getRecordsType.setResultType(ResultType.RESULTS);
    getRecordsType.setStartPosition(BigInteger.valueOf(startIndex));
    getRecordsType.setMaxRecords(BigInteger.valueOf(pageSize));
    getRecordsType.setOutputFormat(MediaType.APPLICATION_XML);
    getRecordsType.setOutputSchema("urn:catalog:metacard");
    QueryType queryType = new QueryType();
    queryType.setTypeNames(
        Arrays.asList(new QName("http://www.opengis.net/cat/csw/2.0.2", "Record", "csw")));

    ElementSetNameType elementSetNameType = new ElementSetNameType();
    elementSetNameType.setValue(ElementSetType.FULL);
    queryType.setElementSetName(elementSetNameType);
    SortByType cswSortBy = new SortByType();
    SortPropertyType sortProperty = new SortPropertyType();
    PropertyNameType propertyName = new PropertyNameType();
    propertyName.setContent(Arrays.asList((Object) "metacard.modified"));
    sortProperty.setPropertyName(propertyName);
    sortProperty.setSortOrder(SortOrderType.ASC);
    cswSortBy.getSortProperty().add(sortProperty);
    queryType.setSortBy(cswSortBy);
    QueryConstraintType queryConstraintType = new QueryConstraintType();
    queryConstraintType.setVersion(CswConstants.CONSTRAINT_VERSION);
    queryConstraintType.setCqlText(cql);
    queryType.setConstraint(queryConstraintType);
    ObjectFactory objectFactory = new ObjectFactory();
    getRecordsType.setAbstractQuery(objectFactory.createQuery(queryType));

    CswRecordCollection response = csw.getRecords(getRecordsType);
    response.getCswRecords();
  }

  public static void main(String[] args) throws Exception {
    System.setProperty(
        "javax.net.ssl.keyStore", "/opt/dib/dib-4.5.2/etc/keystores/serverKeystore.jks");
    System.setProperty("javax.net.ssl.keyStorePassword", "changeit");
    System.setProperty(
        "javax.net.ssl.trustStore", "/opt/dib/dib-4.5.2/etc/keystores/serverTruststore.jks");
    System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
    System.setProperty("javax.net.ssl.keyStoreType", "jks");
    System.setProperty("javax.net.ssl.trustStoreType", "jks");
    System.setProperty(
        "https.cipherSuites",
        "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256,TLS_DHE_RSA_WITH_AES_128_CBC_SHA256,TLS_DHE_RSA_WITH_AES_128_CBC_SHA,TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256");
    System.setProperty("https.protocols", "TLSv1.1,TLSv1.2");
    System.setProperty("jdk.tls.client.protocols", "TLSv1.1,TLSv1.2");
    System.setProperty("jdk.tls.ephemeralDHKeySize", "matched");
    SimpleCsw csw = new SimpleCsw();
    csw.isAvailable();
    csw.query("title like '*'", 1, 100);
  }
}
