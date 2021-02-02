/*
 * Copyright (c) 2020-2021.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.plugin.groups.manager;

import org.drasyl.plugin.groups.manager.data.Group;
import org.drasyl.plugin.groups.manager.database.DatabaseAdapter;
import org.drasyl.plugin.groups.manager.database.DatabaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import spark.Request;
import spark.Response;
import spark.Service;

import java.lang.reflect.Field;
import java.net.InetAddress;

import static java.time.Duration.ofSeconds;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.UNPROCESSABLE_ENTITY_422;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupsManagerApiTest {
    @Mock
    private InetAddress bindHost;
    private final int bindPort = 8080;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DatabaseAdapter database;
    private final Service server = Service.ignite().ipAddress("0.0.0.0").port(0);
    private GroupsManagerApi underTest;

    @BeforeEach
    void setUp() {
        underTest = new GroupsManagerApi(bindHost, bindPort, database, server);
    }

    @Nested
    class Start {
        @Test
        void shouldInitializeServer() throws NoSuchFieldException, IllegalAccessException {
            final Field field = Service.class.getDeclaredField("initialized");
            field.setAccessible(true);

            underTest.start();

            assertTrue((Boolean) field.get(server));
        }
    }

    @Nested
    class Shutdown {
        @Test
        void shouldInitializeServer() throws NoSuchFieldException, IllegalAccessException {
            final Field field = Service.class.getDeclaredField("initialized");
            field.setAccessible(true);
            server.init();
            server.awaitInitialization();

            underTest.shutdown();

            assertFalse((Boolean) field.get(server));
        }
    }

    @Nested
    class GroupsIndex {
        @Test
        void shouldGetGroupsFromDatabase(@Mock final Request request,
                                         @Mock final Response response) throws DatabaseException {
            underTest.groupsIndex(request, response);

            verify(database).getGroups();
        }
    }

    @Nested
    class GroupsCreate {
        @Test
        void shouldCreateGroup(@Mock final Request request,
                               @Mock final Response response) throws DatabaseException {
            when(request.body()).thenReturn("{\n" +
                    "  \"name\": \"steezy-vips\",\n" +
                    "  \"credentials\": \"s3cr3t_passw0rd\",\n" +
                    "  \"minDifficulty\": 6,\n" +
                    "  \"timeout\": 60\n" +
                    "}");
            when(database.addGroup(any())).thenReturn(true);

            underTest.groupsCreate(request, response);

            verify(database).addGroup(Group.of("steezy-vips", "s3cr3t_passw0rd", (byte) 6, ofSeconds(60)));
        }

        @Test
        void shouldReturnErrorOnDuplicateName(@Mock final Request request,
                                              @Mock final Response response) throws DatabaseException {
            when(request.body()).thenReturn("{\n" +
                    "  \"name\": \"steezy-vips\",\n" +
                    "  \"credentials\": \"s3cr3t_passw0rd\",\n" +
                    "  \"minDifficulty\": 6,\n" +
                    "  \"timeout\": 60\n" +
                    "}");
            when(database.addGroup(any())).thenReturn(false);

            assertEquals("Name already taken", underTest.groupsCreate(request, response));
            verify(response).status(UNPROCESSABLE_ENTITY_422);
        }

        @Test
        void shouldReturnErrorOnInvalidJson(@Mock final Request request,
                                            @Mock final Response response) throws DatabaseException {
            when(request.body()).thenReturn("");

            assertThat(underTest.groupsCreate(request, response).toString(), containsString("Unprocessable Entity:"));
            verify(response).status(UNPROCESSABLE_ENTITY_422);
        }
    }

    @Nested
    class GroupsShow {
        @Test
        void shouldGetGroupFromDatabase(@Mock final Request request,
                                        @Mock final Response response) throws DatabaseException {
            when(request.params(":name")).thenReturn("steezy-vips");
            underTest.groupsShow(request, response);

            verify(database).getGroup("steezy-vips");
        }

        @Test
        void shouldReturnErrorOnNonExistingGroup(@Mock final Request request,
                                                 @Mock final Response response) throws DatabaseException {
            when(database.getGroup(any())).thenReturn(null);

            assertEquals("Not Found", underTest.groupsShow(request, response));
            verify(response).status(NOT_FOUND_404);
        }
    }

    @Nested
    class GroupsUpdate {
        @Test
        void shouldUpdateGroup(@Mock final Request request,
                               @Mock final Response response) throws DatabaseException {
            when(request.params(":name")).thenReturn("steezy-vips");
            when(request.body()).thenReturn("{\n" +
                    "  \"credentials\": \"s3cr3t_passw0rd\",\n" +
                    "  \"minDifficulty\": 6,\n" +
                    "  \"timeout\": 90\n" +
                    "}");

            underTest.groupsUpdate(request, response);

            verify(database).updateGroup(Group.of("steezy-vips", "s3cr3t_passw0rd", (byte) 6, ofSeconds(90)));
        }

        @Test
        void shouldReturnErrorOnNonExistingGroup(@Mock final Request request,
                                                 @Mock final Response response) throws DatabaseException {
            when(database.getGroup(any())).thenReturn(null);

            assertEquals("Not Found", underTest.groupsUpdate(request, response));
            verify(response).status(NOT_FOUND_404);
        }

        @Test
        void shouldReturnErrorOnInvalidJson(@Mock final Request request,
                                            @Mock final Response response) throws DatabaseException {
            when(request.body()).thenReturn("");

            assertThat(underTest.groupsUpdate(request, response), containsString("Unprocessable Entity:"));
            verify(response).status(UNPROCESSABLE_ENTITY_422);
        }
    }

    @Nested
    class GroupsDelete {
        @Test
        void shouldDeleteGroup(@Mock final Request request,
                               @Mock final Response response) throws DatabaseException {
            when(request.params(":name")).thenReturn("steezy-vips");
            when(database.deleteGroup(any())).thenReturn(true);

            underTest.groupsDelete(request, response);

            verify(database).deleteGroup("steezy-vips");
        }

        @Test
        void shouldReturnErrorOnNonExistingGroup(@Mock final Request request,
                                                 @Mock final Response response) throws DatabaseException {
            assertEquals("Not Found", underTest.groupsDelete(request, response));
            verify(response).status(NOT_FOUND_404);
        }
    }

    @Nested
    class GroupsMembershipsIndex {
        @Test
        void shouldDeleteGroup(@Mock final Request request,
                               @Mock final Response response) throws DatabaseException {
            when(request.params(":name")).thenReturn("steezy-vips");

            underTest.groupsMembershipsIndex(request, response);

            verify(database).getGroupMembers("steezy-vips");
        }

        @Test
        void shouldReturnErrorOnNonExistingGroup(@Mock final Request request,
                                                 @Mock final Response response) throws DatabaseException {
            when(database.getGroup(any())).thenReturn(null);

            assertEquals("Not Found", underTest.groupsMembershipsIndex(request, response));
            verify(response).status(NOT_FOUND_404);
        }
    }

    @Nested
    class JsonTransformerTest {
        @Test
        void shouldReturnJson() throws Exception {
            final String json = new GroupsManagerApi.JsonTransformer().render(Group.of("steezy-vips", "secret"));
            assertThatJson(json)
                    .isObject()
                    .containsKeys("name", "credentials", "minDifficulty", "timeout");
        }
    }
}
