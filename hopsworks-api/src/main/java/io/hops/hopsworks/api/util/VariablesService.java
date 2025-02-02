/*
 * Changes to this file committed after and not including commit-id: ccc0d2c5f9a5ac661e60e6eaf138de7889928b8b
 * are released under the following license:
 *
 * This file is part of Hopsworks
 * Copyright (C) 2018, Logical Clocks AB. All rights reserved
 *
 * Hopsworks is free software: you can redistribute it and/or modify it under the terms of
 * the GNU Affero General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * Hopsworks is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE.  See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * Changes to this file committed before and including commit-id: ccc0d2c5f9a5ac661e60e6eaf138de7889928b8b
 * are released under the following license:
 *
 * Copyright (C) 2013 - 2018, Logical Clocks AB and RISE SICS AB. All rights reserved
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS  OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL  THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR  OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package io.hops.hopsworks.api.util;

import io.hops.hopsworks.api.filter.Audience;
import io.hops.hopsworks.api.filter.JWTNotRequired;
import io.hops.hopsworks.api.filter.NoCacheResponse;
import io.hops.hopsworks.api.filter.apiKey.ApiKeyRequired;
import io.hops.hopsworks.common.dao.remote.oauth.OauthClientFacade;
import io.hops.hopsworks.common.dataset.FolderNameValidator;
import io.hops.hopsworks.common.remote.RemoteUserHelper;
import io.hops.hopsworks.common.util.Settings;
import io.hops.hopsworks.exceptions.GenericException;
import io.hops.hopsworks.exceptions.HopsSecurityException;
import io.hops.hopsworks.exceptions.ServiceException;
import io.hops.hopsworks.jwt.annotation.JWTRequired;
import io.hops.hopsworks.persistence.entity.remote.oauth.OauthClient;
import io.hops.hopsworks.persistence.entity.user.security.apiKey.ApiScope;
import io.hops.hopsworks.persistence.entity.util.Variables;
import io.hops.hopsworks.persistence.entity.util.VariablesVisibility;
import io.hops.hopsworks.restutils.RESTCodes;
import io.swagger.annotations.Api;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

@Path("/variables")
@Stateless
@Api(value = "Variables Service", description = "Variables Service")
@TransactionAttribute(TransactionAttributeType.NEVER)
public class VariablesService {

  @EJB
  private NoCacheResponse noCacheResponse;
  @EJB
  private Settings settings;
  @EJB
  private OauthClientFacade oauthClientFacade;
  @Inject
  private RemoteUserHelper remoteUserHelper;

  @GET
  @Path("{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @JWTRequired(acceptedTokens={Audience.API, Audience.JOB}, allowedUserRoles={"HOPS_ADMIN", "HOPS_USER",
    "HOPS_SERVICE_USER"})
  @ApiKeyRequired(acceptedScopes = {ApiScope.PROJECT},
    allowedUserRoles = {"HOPS_ADMIN", "HOPS_USER", "HOPS_SERVICE_USER"})
  public Response getVar(@Context SecurityContext sc, @PathParam("id") String id)
      throws ServiceException, HopsSecurityException {
    Variables variable = settings.findById(id)
        .orElseThrow(() -> new ServiceException(RESTCodes.ServiceErrorCode.VARIABLE_NOT_FOUND, Level.FINE,
            "Variable: " + id + "not found"));

    if (variable.getVisibility() == VariablesVisibility.ADMIN && !sc.isUserInRole("HOPS_ADMIN")) {
      throw new HopsSecurityException(RESTCodes.SecurityErrorCode.REST_ACCESS_CONTROL, Level.FINE,
          "The requested variable requires admin privileges");
    }

    RESTApiJsonResponse json = new RESTApiJsonResponse();
    json.setSuccessMessage(variable.getValue());
    return Response.ok().entity(json).build();
  }

  @GET
  @Path("twofactor")
  @Produces(MediaType.APPLICATION_JSON)
  @JWTNotRequired
  public Response getTwofactor() {
    RESTApiJsonResponse json = new RESTApiJsonResponse();
    json.setSuccessMessage(settings.getTwoFactorAuth());
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(json).build();
  }

  @GET
  @Path("ldap")
  @Produces(MediaType.APPLICATION_JSON)
  @JWTNotRequired
  public Response getLDAPAuthStatus() {
    RESTApiJsonResponse json = new RESTApiJsonResponse();
    json.setSuccessMessage(settings.getLDAPAuthStatus());
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(json).build();
  }

  @GET
  @Path("authStatus")
  @Produces(MediaType.APPLICATION_JSON)
  @JWTNotRequired
  @Deprecated
  public Response getAuthStatus() {
    List<OauthClient> oauthClients = oauthClientFacade.findAll();
    List<OpenIdProvider> providers = new ArrayList<>();
    for (OauthClient client : oauthClients) {
      providers.add(
        new OpenIdProvider(client.getProviderName(), client.getProviderDisplayName(), client.getProviderLogoURI()));
    }
    AuthStatus authStatus = new AuthStatus(settings.getTwoFactorAuth(), settings.getLDAPAuthStatus(), settings.
      getKRBAuthStatus(), settings.isPasswordLoginDisabled(), settings.isRegistrationUIDisabled(), providers);
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(authStatus).build();
  }

  @GET
  @Path("authenticationStatus")
  @Produces(MediaType.APPLICATION_JSON)
  @JWTNotRequired
  public Response getAuthenticationStatus() {
    boolean remoteAuthEnabled = remoteUserHelper.isRemoteUserAuthAvailable();

    List<OpenIdProvider> providers = new ArrayList<>();
    if (remoteAuthEnabled) {
      List<OauthClient> oauthClients = oauthClientFacade.findAll();
      for (OauthClient client : oauthClients) {
        providers.add(new OpenIdProvider(client.getProviderName(), client.getProviderDisplayName(),
          client.getProviderLogoURI()));
      }
    }
    AuthenticationStatus authenticationStatus = new AuthenticationStatus(
      OTPAuthStatus.fromTwoFactorMode(settings.getTwoFactorAuth()), settings.isLdapEnabled(), settings.isKrbEnabled(),
      settings.isOAuthEnabled(), providers, settings.isPasswordLoginDisabled(), settings.isRegistrationUIDisabled(),
      remoteAuthEnabled);
    return Response.ok(authenticationStatus).build();
  }

  @GET
  @Path("versions")
  @Produces(MediaType.APPLICATION_JSON)
  @JWTNotRequired
  public Response getVersions(){
    VersionsDTO dto = new VersionsDTO(settings);
    List<VersionsDTO.Version> list = dto.getVersions();
    Collections.sort(list);
    GenericEntity<List<VersionsDTO.Version>> versions
        = new GenericEntity<List<VersionsDTO.Version>>(list) { };

    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(versions).build();
  }
  
  @GET
  @Path("/conda")
  @Produces(MediaType.TEXT_PLAIN)
  @JWTNotRequired
  public Response getCondaDefaultRepo() {
    String defaultRepo = settings.getCondaDefaultRepo();
    if (settings.isAnacondaEnabled()) {
      return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(defaultRepo).build();
    }
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.SERVICE_UNAVAILABLE).build();
  }

  @GET
  @Path("filename-regex")
  @Produces(MediaType.APPLICATION_JSON)
  @JWTNotRequired
  public Response getFileNameValidatorRegex(@QueryParam("type") String type) throws GenericException {
    FileNameRegexDTO fileNameRegexDTO = new FileNameRegexDTO();
    if (type == null || type.equals("project")) {
      fileNameRegexDTO.setRegex(FolderNameValidator.getProjectNameRegexStr(settings.getReservedProjectNames()));
      fileNameRegexDTO.setReservedWords(settings.getProjectNameReservedWords().toUpperCase());
    } else if (type.equals("dataset")) {
      fileNameRegexDTO.setRegex(FolderNameValidator.getDatasetNameRegexStr());
    } else {
      throw new GenericException(RESTCodes.GenericErrorCode.ILLEGAL_ARGUMENT, Level.FINE, "Type QueryParam should be:" +
        "project|dataset|subdir");
    }
    return Response.ok(fileNameRegexDTO).build();
  }

}
