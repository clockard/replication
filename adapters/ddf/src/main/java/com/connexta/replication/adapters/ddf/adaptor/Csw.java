package com.connexta.replication.adapters.ddf.adaptor;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import net.opengis.cat.csw.v_2_0_2.CapabilitiesType;
import net.opengis.cat.csw.v_2_0_2.GetCapabilitiesType;
import net.opengis.cat.csw.v_2_0_2.GetRecordsType;

/** JAX-RS Interface to define an OGC Catalogue Service for Web (CSW). */
@Path("/")
public interface Csw {

  /**
   * GetCapabilities - HTTP POST
   *
   * @param request
   * @return
   */
  @POST
  @Consumes({MediaType.TEXT_XML, MediaType.APPLICATION_XML})
  @Produces({MediaType.TEXT_XML, MediaType.APPLICATION_XML})
  CapabilitiesType getCapabilities(GetCapabilitiesType request) throws Exception;

  /**
   * GetRecords - HTTP POST
   *
   * @param request
   * @return
   */
  @POST
  @Consumes({MediaType.TEXT_XML, MediaType.APPLICATION_XML})
  @Produces({MediaType.TEXT_XML, MediaType.APPLICATION_XML})
  CswRecordCollection getRecords(GetRecordsType request) throws Exception;
}
