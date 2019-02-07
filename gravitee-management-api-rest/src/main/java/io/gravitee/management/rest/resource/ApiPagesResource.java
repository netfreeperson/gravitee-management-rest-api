/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.management.rest.resource;

import io.gravitee.common.http.MediaType;
import io.gravitee.management.model.NewPageEntity;
import io.gravitee.management.model.PageEntity;
import io.gravitee.management.model.PageType;
import io.gravitee.management.model.Visibility;
import io.gravitee.management.model.api.ApiEntity;
import io.gravitee.management.model.documentation.PageQuery;
import io.gravitee.management.model.permissions.RolePermission;
import io.gravitee.management.model.permissions.RolePermissionAction;
import io.gravitee.management.rest.security.Permission;
import io.gravitee.management.rest.security.Permissions;
import io.gravitee.management.service.ApiService;
import io.gravitee.management.service.GroupService;
import io.gravitee.management.service.PageService;
import io.gravitee.management.service.exceptions.ForbiddenAccessException;
import io.swagger.annotations.*;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com)
 * @author Guillaume GILLON (guillaume.gillon@outlook.com)
 * @author GraviteeSource Team
 */
@Api(tags = {"API"})
public class ApiPagesResource extends AbstractResource {

    @Inject
    private ApiService apiService;

    @Inject
    private PageService pageService;

    @Inject
    private GroupService groupService;

    @Context
    private ResourceContext resourceContext;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "List pages",
            notes = "User must have the READ permission to use this service")
    @ApiResponses({
            @ApiResponse(code = 200, message = "List of pages", response = PageEntity.class, responseContainer = "List"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public List<PageEntity> listPages(
            @PathParam("api") String api,
            @QueryParam("homepage") Boolean homepage,
            @QueryParam("type") PageType type,
            @QueryParam("parent") String parent,
            @QueryParam("name") String name,
            @QueryParam("root") Boolean rootParent) {
        final ApiEntity apiEntity = apiService.findById(api);
        if (Visibility.PUBLIC.equals(apiEntity.getVisibility())
                || hasPermission(RolePermission.API_DOCUMENTATION, api, RolePermissionAction.READ)) {

            return pageService
                    .search(new PageQuery.Builder()
                            .api(api)
                            .homepage(homepage)
                            .type(type)
                            .parent(parent)
                            .name(name)
                            .rootParent(rootParent)
                            .build())
                    .stream()
                    .filter(page -> isDisplayable(apiEntity, page.isPublished(), page.getExcludedGroups()))
                    .collect(Collectors.toList());
        }
        throw new ForbiddenAccessException();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create a page",
            notes = "User must have the MANAGE_PAGES permission to use this service")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Page successfully created", response = PageEntity.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.API_DOCUMENTATION, acls = RolePermissionAction.CREATE)
    })
    public Response createPage(
            @PathParam("api") String api,
            @ApiParam(name = "page", required = true) @Valid @NotNull NewPageEntity newPageEntity) {
        int order = pageService.findMaxApiPageOrderByApi(api) + 1;
        newPageEntity.setOrder(order);
        newPageEntity.setLastContributor(getAuthenticatedUser());
        PageEntity newPage = pageService.createPage(api, newPageEntity);
        if (newPage != null) {
            return Response
                    .created(URI.create("/apis/" + api + "/pages/" + newPage.getId()))
                    .entity(newPage)
                    .build();
        }

        return Response.serverError().build();
    }

    @POST
    @Path("/_fetch")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Refresh all pages by calling their associated fetcher",
            notes = "User must have the MANAGE_PAGES permission to use this service")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Pages successfully refreshed", response = PageEntity.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.API_DOCUMENTATION, acls = RolePermissionAction.UPDATE)
    })
    public Response fetchAllPages(
            @PathParam("api") String api
    ) {
        List<PageEntity> pages = pageService.search(new PageQuery.Builder().api(api).build());
        String contributor = getAuthenticatedUser();

        pages.stream()
                .filter(pageListItem -> pageListItem.getSource() != null)
                .forEach(pageListItem -> pageService.fetch(pageListItem.getId(), contributor));

        return Response.noContent().build();
    }

    @Path("{page}")
    public ApiPageResource getApiPageResource() {
        return resourceContext.getResource(ApiPageResource.class);
    }

    @POST
    @Path("/_import")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Import pages",
            notes = "User must be ADMIN to use this service")
    @ApiResponses({
            @ApiResponse(code = 201, message = "Page successfully created", response = PageEntity.class),
            @ApiResponse(code = 500, message = "Internal server error")})
    @Permissions({
            @Permission(value = RolePermission.API_DOCUMENTATION, acls = RolePermissionAction.CREATE)
    })
    public List<PageEntity> importFiles(
            @PathParam("api") String api,
            @ApiParam(name = "page", required = true) @Valid @NotNull NewPageEntity newPageEntity) {
        newPageEntity.setLastContributor(getAuthenticatedUser());
        return pageService.importDirectory(api, newPageEntity);
    }

    private boolean isDisplayable(ApiEntity api, boolean isPagePublished, List<String> excludedGroups) {
        return (isAuthenticated() && isAdmin())
                ||
                ( pageService.isDisplayable(api, isPagePublished, getAuthenticatedUserOrNull()) &&
                        groupService.isUserAuthorizedToAccessApiData(api, excludedGroups, getAuthenticatedUserOrNull()));

    }
}
