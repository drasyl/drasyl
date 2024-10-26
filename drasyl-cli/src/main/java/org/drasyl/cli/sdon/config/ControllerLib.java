/*
 * Copyright (c) 2020-2024 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.drasyl.cli.sdon.config;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;

/**
 * Lua API provided by the controller.
 */
public class ControllerLib extends TwoArgFunction {
    public static Network network;

    @Override
    public LuaValue call(final LuaValue modname, final LuaValue env) {
        final LuaValue library = tableOf();
        env.set("create_network", new CreateNetworkFunction());
        env.set("register_network", new RegisterNetworkFunction());
        env.set("inspect", new InspectFunction());
        return library;
    }

    static class CreateNetworkFunction extends OneArgFunction {
        @Override
        public LuaValue call(final LuaValue paramsArg) {
            return new Network(paramsArg);
        }
    }

    static class RegisterNetworkFunction extends OneArgFunction {
        @Override
        public LuaValue call(final LuaValue networkArg) {
            final LuaTable networkTable = networkArg.checktable();

            if (network != null) {
                throw new LuaError("Only one network can be registered.");
            }

            network = (Network) networkTable;

            return NIL;
        }
    }

    static class InspectFunction extends OneArgFunction {
        @Override
        public LuaValue call(final LuaValue arg) {
            return LuaValue.valueOf(arg.toString());
        }
    }
}
