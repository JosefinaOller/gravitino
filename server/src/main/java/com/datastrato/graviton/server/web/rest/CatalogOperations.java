package com.datastrato.graviton.server.web.rest;

import com.datastrato.graviton.Catalog;
import com.datastrato.graviton.CatalogChange;
import com.datastrato.graviton.NameIdentifier;
import com.datastrato.graviton.Namespace;
import com.datastrato.graviton.dto.CatalogDTO;
import com.datastrato.graviton.dto.requests.CatalogCreateRequest;
import com.datastrato.graviton.dto.requests.CatalogUpdateRequest;
import com.datastrato.graviton.dto.requests.CatalogUpdatesRequest;
import com.datastrato.graviton.dto.responses.CatalogListResponse;
import com.datastrato.graviton.dto.responses.CatalogResponse;
import com.datastrato.graviton.exceptions.CatalogAlreadyExistsException;
import com.datastrato.graviton.exceptions.NoSuchCatalogException;
import com.datastrato.graviton.exceptions.NoSuchMetalakeException;
import com.datastrato.graviton.meta.BaseCatalogsOperations;
import com.datastrato.graviton.server.web.Utils;
import java.util.Arrays;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/metalakes/{metalake}/catalogs")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CatalogOperations {

  private static final Logger LOG = LoggerFactory.getLogger(CatalogOperations.class);

  private final BaseCatalogsOperations ops;

  @Context private HttpServletRequest httpRequest;

  @Inject
  public CatalogOperations(BaseCatalogsOperations ops) {
    this.ops = ops;
  }

  @GET
  @Produces("application/vnd.graviton.v1+json")
  public Response listCatalogs(@PathParam("metalake") String metalake) {
    if (metalake == null || metalake.isEmpty()) {
      LOG.error("Metalake name is null or empty");
      return Utils.illegalArguments("Metalake name is null or empty");
    }

    try {
      Namespace metalakeNS = Namespace.of(metalake);
      Catalog[] catalogs = ops.listCatalogs(metalakeNS);

      CatalogDTO[] catalogDTOS =
          Arrays.stream(catalogs).map(DTOConverters::toDTO).toArray(CatalogDTO[]::new);
      return Utils.ok(new CatalogListResponse(catalogDTOS));

    } catch (NoSuchMetalakeException ex) {
      LOG.error("Metalake {} does not exist, fail to list catalogs", metalake);
      return Utils.notFound("Metalake " + metalake + " does not exist");

    } catch (Exception e) {
      LOG.error("Failed to list catalogs under metalake {}", metalake, e);
      return Utils.internalError(e.getMessage());
    }
  }

  @POST
  @Produces("application/vnd.graviton.v1+json")
  public Response createCatalog(
      @PathParam("metalake") String metalake, CatalogCreateRequest request) {
    if (metalake == null || metalake.isEmpty()) {
      LOG.error("Metalake name is null or empty");
      return Utils.illegalArguments("Metalake name is null or empty");
    }

    try {
      request.validate();
    } catch (IllegalArgumentException e) {
      LOG.error("Failed to validate create Catalog arguments {}", request, e);
      return Utils.illegalArguments(e.getMessage());
    }

    try {
      NameIdentifier ident = NameIdentifier.of(metalake, request.getName());
      Catalog catalog =
          ops.createCatalog(
              ident, request.getType(), request.getComment(), request.getProperties());
      return Utils.ok(new CatalogResponse(DTOConverters.toDTO(catalog)));

    } catch (NoSuchMetalakeException ex) {
      LOG.error("Metalake {} does not exist, fail to create catalog", metalake);
      return Utils.notFound("Metalake " + metalake + " does not exist");

    } catch (CatalogAlreadyExistsException ex) {
      LOG.error("Catalog {} already exists under metalake {}", request.getName(), metalake);
      return Utils.alreadyExists(
          String.format(
              "Catalog %s already exists under metalake %s", request.getName(), metalake));

    } catch (Exception e) {
      LOG.error("Failed to create catalog under metalake {}", metalake, e);
      return Utils.internalError(e.getMessage());
    }
  }

  @GET
  @Path("{catalog}")
  @Produces("application/vnd.graviton.v1+json")
  public Response loadCatalog(
      @PathParam("metalake") String metalakeName, @PathParam("catalog") String catalogName) {
    if (metalakeName == null || metalakeName.isEmpty()) {
      LOG.error("Metalake name is null or empty");
      return Utils.illegalArguments("Metalake name is null or empty");
    }

    if (catalogName == null || catalogName.isEmpty()) {
      LOG.error("Catalog name is null or empty");
      return Utils.illegalArguments("Catalog name is null or empty");
    }

    try {
      NameIdentifier ident = NameIdentifier.of(metalakeName, catalogName);
      Catalog catalog = ops.loadCatalog(ident);
      return Utils.ok(new CatalogResponse(DTOConverters.toDTO(catalog)));

    } catch (NoSuchMetalakeException ex) {
      LOG.error("Metalake {} does not exist, fail to load catalog {}", metalakeName, catalogName);
      return Utils.notFound("Metalake " + metalakeName + " does not exist");

    } catch (NoSuchCatalogException ex) {
      LOG.error("Catalog {} does not exist under metalake {}", catalogName, metalakeName);
      return Utils.notFound(
          String.format("Catalog %s does not exist under metalake %s", catalogName, metalakeName));

    } catch (Exception e) {
      LOG.error("Failed to load catalog {} under metalake {}", catalogName, metalakeName, e);
      return Utils.internalError(e.getMessage());
    }
  }

  @PUT
  @Path("{catalog}")
  @Produces("application/vnd.graviton.v1+json")
  public Response alterCatalog(
      @PathParam("metalake") String metalakeName,
      @PathParam("catalog") String catalogName,
      CatalogUpdatesRequest request) {
    if (metalakeName == null || metalakeName.isEmpty()) {
      LOG.error("Metalake name is null or empty");
      return Utils.illegalArguments("Metalake name is null or empty");
    }

    if (catalogName == null || catalogName.isEmpty()) {
      LOG.error("Catalog name is null or empty");
      return Utils.illegalArguments("Catalog name is null or empty");
    }

    try {
      request.validate();
    } catch (IllegalArgumentException e) {
      LOG.error("Failed to validate alter Catalog arguments {}", request, e);
      return Utils.illegalArguments(e.getMessage());
    }

    try {
      NameIdentifier ident = NameIdentifier.of(metalakeName, catalogName);
      CatalogChange[] changes =
          request.getRequests().stream()
              .map(CatalogUpdateRequest::catalogChange)
              .toArray(CatalogChange[]::new);

      Catalog catalog = ops.alterCatalog(ident, changes);
      return Utils.ok(new CatalogResponse(DTOConverters.toDTO(catalog)));

    } catch (NoSuchCatalogException ex) {
      LOG.error("Catalog {} does not exist under metalake {}", catalogName, metalakeName);
      return Utils.notFound(ex.getMessage());

    } catch (IllegalArgumentException ex) {
      LOG.error(
          "Failed to alter catalog {} under metalake {} with unsupported changes",
          catalogName,
          metalakeName,
          ex);
      return Utils.illegalArguments(ex.getMessage());

    } catch (Exception e) {
      LOG.error("Failed to alter catalog {} under metalake {}", catalogName, metalakeName, e);
      return Utils.internalError(e.getMessage());
    }
  }

  @DELETE
  @Path("{catalog}")
  @Produces("application/vnd.graviton.v1+json")
  public Response dropCatalog(
      @PathParam("metalake") String metalakeName, @PathParam("catalog") String catalogName) {
    if (metalakeName == null || metalakeName.isEmpty()) {
      LOG.error("Metalake name is null or empty");
      return Utils.illegalArguments("Metalake name is null or empty");
    }

    if (catalogName == null || catalogName.isEmpty()) {
      LOG.error("Catalog name is null or empty");
      return Utils.illegalArguments("Catalog name is null or empty");
    }

    try {
      NameIdentifier ident = NameIdentifier.of(metalakeName, catalogName);
      boolean dropped = ops.dropCatalog(ident);
      if (dropped) {
        return Utils.ok();
      } else {
        LOG.warn("Failed to drop catalog {} under metalake {}", catalogName, metalakeName);
        return Utils.internalError("Failed to drop catalog " + catalogName);
      }

    } catch (Exception e) {
      LOG.error("Failed to drop catalog {} under metalake {}", catalogName, metalakeName, e);
      return Utils.internalError(e.getMessage());
    }
  }
}