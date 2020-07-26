/*
 * Copyright (c) 2020.
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
package org.drasyl.plugins;

import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.pipeline.Handler;
import org.drasyl.pipeline.HandlerAdapter;
import org.drasyl.pipeline.Pipeline;
import org.drasyl.util.DrasylFunction;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginManagerTest {
    @Mock
    private Map<String, DrasylPlugin> plugins;
    @Mock
    private Pipeline pipeline;
    @Mock
    private DrasylConfig config;
    @Mock
    private static HandlerAdapter handler;
    @Mock
    private DrasylFunction<Class<? extends AutoloadablePlugin>, Constructor<?>, Exception> constructorFunction;

    @Test
    void shouldNotLoadAnyPluginOnEmptyList() throws DrasylException {
        PluginManager manager = new PluginManager(pipeline, config, plugins, constructorFunction);
        manager.open();

        verifyNoInteractions(plugins);
    }

    @Test
    void shouldAddPlugin() {
        PluginManager manager = new PluginManager(pipeline, config, plugins, constructorFunction);

        DrasylPlugin plugin = mock(DrasylPlugin.class);
        Handler handler = mock(Handler.class);
        when(plugin.getHandler()).thenReturn(List.of(handler));
        when(plugin.name()).thenReturn("PluginName");

        manager.add(plugin);

        verify(plugins).put(plugin.name(), plugin);
        verify(pipeline).addLast(handler.getClass().getSimpleName(), handler);
        verify(plugin).onAdded();
    }

    @Test
    void shouldRemovePlugin() {
        DrasylPlugin plugin = mock(DrasylPlugin.class);
        when(plugin.name()).thenReturn("PluginName");
        when(plugins.remove(plugin.name())).thenReturn(plugin);
        Handler handler = mock(Handler.class);
        when(plugin.getHandler()).thenReturn(List.of(handler));

        PluginManager manager = new PluginManager(pipeline, config, plugins, constructorFunction);

        manager.remove(plugin.name());

        verify(plugins).remove(eq(plugin.name()));
        verify(pipeline).remove(eq(handler.getClass().getSimpleName()));
        verify(plugin).onRemove();
    }

    @Test
    void shouldRemovePluginsOnStop() {
        DrasylPlugin plugin = mock(DrasylPlugin.class);
        when(plugins.values()).thenReturn(List.of(plugin));
        Handler handler = mock(Handler.class);
        when(plugin.getHandler()).thenReturn(List.of(handler));

        PluginManager manager = new PluginManager(pipeline, config, plugins, constructorFunction);

        manager.close();

        verify(pipeline).remove(eq(handler.getClass().getSimpleName()));
        verify(plugin).onRemove();
        verify(plugins).clear();
    }

    @Test
    void shouldLoadAllPluginsThatAreDefinedInTheDrasylConfig() throws DrasylException {
        PluginEnvironment env = mock(PluginEnvironment.class);

        when(config.getPluginEnvironments()).thenReturn(List.of(env));
        doReturn(PluginManagerTest.TestPlugin.class).when(env).getClazz();

        constructorFunction = clazz -> clazz.getConstructor(Pipeline.class, DrasylConfig.class, PluginEnvironment.class);

        PluginManager manager = new PluginManager(pipeline, config, plugins, constructorFunction);
        manager.open();

        verify(plugins).put(isA(String.class), isA(TestPlugin.class));
        verify(pipeline).addLast(handler.getClass().getSimpleName(), handler);
    }

    @Nested
    class ExceptionRethrowing {
        @Test
        void rethrowNoSuchMethodException() throws Exception {
            PluginEnvironment env = mock(PluginEnvironment.class);
            doReturn(PluginManagerTest.TestPlugin.class).when(env).getClazz();
            when(constructorFunction.apply(any())).thenThrow(NoSuchMethodException.class);

            PluginManager manager = new PluginManager(pipeline, config, plugins, constructorFunction);
            assertThrows(DrasylException.class, () -> manager.loadPlugin(env));
        }

        @Test
        void rethrowIllegalAccessException() throws Exception {
            PluginEnvironment env = mock(PluginEnvironment.class);
            doReturn(PluginManagerTest.TestPlugin.class).when(env).getClazz();
            when(constructorFunction.apply(any())).thenThrow(IllegalAccessException.class);

            PluginManager manager = new PluginManager(pipeline, config, plugins, constructorFunction);
            assertThrows(DrasylException.class, () -> manager.loadPlugin(env));
        }

        @Test
        void rethrowInstantiationException() throws Exception {
            PluginEnvironment env = mock(PluginEnvironment.class);
            doReturn(PluginManagerTest.TestPlugin.class).when(env).getClazz();
            when(constructorFunction.apply(any())).thenThrow(InstantiationException.class);

            PluginManager manager = new PluginManager(pipeline, config, plugins, constructorFunction);
            assertThrows(DrasylException.class, () -> manager.loadPlugin(env));
        }

        @Test
        void rethrowExceptionn() throws Exception {
            PluginEnvironment env = mock(PluginEnvironment.class);
            doReturn(PluginManagerTest.TestPlugin.class).when(env).getClazz();
            when(constructorFunction.apply(any())).thenThrow(Exception.class);

            PluginManager manager = new PluginManager(pipeline, config, plugins, constructorFunction);
            assertThrows(DrasylException.class, () -> manager.loadPlugin(env));
        }
    }

    public static class TestPlugin extends AutoloadablePlugin {
        public TestPlugin(Pipeline pipeline,
                          DrasylConfig config,
                          PluginEnvironment environment) {
            super(pipeline, config, environment);
        }

        @Override
        public List<Handler> getHandler() {
            return List.of(handler);
        }

        @Override
        public String name() {
            return "PluginManagerTest.TestPlugin";
        }

        @Override
        public String description() {
            return "This is a test plugin.";
        }

        @Override
        public void onRemove() {
            // Do nothing
        }

        @Override
        public void onAdded() {
            // Do nothing
        }
    }
}