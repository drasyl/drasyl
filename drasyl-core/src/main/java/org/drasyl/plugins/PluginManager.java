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
import org.drasyl.pipeline.Pipeline;
import org.drasyl.util.DrasylFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

public class PluginManager {
    private static final Logger LOG = LoggerFactory.getLogger(PluginManager.class);
    private final Map<String, DrasylPlugin> plugins;
    private final Pipeline pipeline;
    private final DrasylConfig config;
    private final DrasylFunction<Class<? extends AutoloadablePlugin>, Constructor<?>, Exception> constructorFunction;

    public PluginManager(Pipeline pipeline,
                         DrasylConfig config) {
        this(pipeline, config, new HashMap<>(), clazz -> clazz.getConstructor(Pipeline.class, DrasylConfig.class, PluginEnvironment.class));
    }

    PluginManager(Pipeline pipeline,
                  DrasylConfig config,
                  Map<String, DrasylPlugin> plugins,
                  DrasylFunction<Class<? extends AutoloadablePlugin>, Constructor<?>, Exception> constructorFunction) {
        this.pipeline = pipeline;
        this.config = config;
        this.plugins = plugins;
        this.constructorFunction = constructorFunction;
    }

    /**
     * This method gets called after the drasyl node was started.
     */
    public synchronized void afterStart() {
        for (DrasylPlugin plugin : plugins.values()) {
            plugin.onAfterStart();
        }
    }

    /**
     * This method gets called after the drasyl node was stopped and will be remove all plugins from
     * the internal plugin list. <br />
     * <b>At this point, no communication channel is alive.</b>
     */
    public synchronized void afterStop() {
        LOG.debug("Stop Plugins...");

        for (DrasylPlugin plugin : plugins.values()) {
            plugin.onAfterStop();
        }

        plugins.clear();
        LOG.debug("Plugins stopped");
    }

    /**
     * This method gets called before the drasyl node is started and automatically loads all plugins
     * that are defined in the corresponding {@link DrasylConfig}. <br />
     * <b>At this point, no communication channel is alive.</b>
     */
    public synchronized void beforeStart() throws DrasylException {
        LOG.debug("Start Plugins...");
        for (PluginEnvironment pluginEnvironment : config.getPluginEnvironments()) {
            AutoloadablePlugin plugin = loadPlugin(pluginEnvironment);
            add(plugin);
        }
        LOG.debug("Plugins started.");
    }

    AutoloadablePlugin loadPlugin(PluginEnvironment pluginEnvironment) throws DrasylException {
        try {
            Constructor<?> constructor = constructorFunction.apply(pluginEnvironment.getClazz());
            return (AutoloadablePlugin) constructor.newInstance(pipeline, config, pluginEnvironment);
        }
        catch (NoSuchMethodException e) {
            LOG.error("", e);
            throw new DrasylException("The given plugin `" + pluginEnvironment.getClazz().getSimpleName() + "` has not the correct signature");
        }
        catch (IllegalAccessException e) {
            LOG.error("", e);
            throw new DrasylException("Can't access the given plugin `" + pluginEnvironment.getClazz().getSimpleName() + "`");
        }
        catch (InstantiationException e) {
            LOG.error("", e);
            throw new DrasylException("Can't invoke the given plugin `" + pluginEnvironment.getClazz().getSimpleName() + "`");
        }
        catch (Exception e) {
            LOG.error("", e);
            throw new DrasylException("Can't instantiate the given plugin `" + pluginEnvironment.getClazz().getSimpleName() + "`");
        }
    }

    /**
     * Adds a {@link DrasylPlugin} to the {@link PluginManager} and also to the {@link
     * org.drasyl.pipeline.Pipeline}.
     *
     * @param plugin the plugin that should be added
     */
    void add(DrasylPlugin plugin) throws DrasylException {
        duplicateCheck(plugin.name());
        plugins.put(plugin.name(), plugin);
        plugin.onBeforeStart();
    }

    /**
     * Checks if the plugin is already added and throws an exception in this case.
     *
     * @param name the plugin name
     * @throws DrasylException if plugin is already added
     */
    private void duplicateCheck(String name) throws DrasylException {
        if (plugins.containsKey(name)) {
            throw new DrasylException("Can't add the '" + name + "' plugin twice.");
        }
    }

    /**
     * This method get called before the drasyl node is stopped.
     */
    public synchronized void beforeStop() {
        for (DrasylPlugin plugin : plugins.values()) {
            plugin.onBeforeStop();
        }
    }
}