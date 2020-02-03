/*
 * Copyright (c) 2020
 *
 * This file is part of drasyl.
 *
 * drasyl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * drasyl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

import Vue from 'vue'
import Vuex from 'vuex'

Vue.use(Vuex);

export default new Vuex.Store({
    state: {
        socket: {
            isConnected: false,
            status: '',
            reconnectError: false,
        }
    },
    mutations:{
        SOCKET_ONOPEN (state, event)  {
            Vue.prototype.$socket = event.currentTarget;
            state.socket.isConnected = true;
            sendHello()
        },
        SOCKET_ONCLOSE (state, event)  {
            event;
            state.socket.isConnected = false
        },
        SOCKET_ONERROR (state, event)  {
            window.console.error(state, event)
        },
        // default handler called for all methods
        SOCKET_ONMESSAGE (state, message)  {
            state.socket.status = message
        },
        // mutations for reconnect methods
        SOCKET_RECONNECT(state, count) {
            window.console.info(state, count);
            sendHello()
        },
        SOCKET_RECONNECT_ERROR(state) {
            state.socket.reconnectError = true;
        },
    },
    actions: {
        sendMessage: function({commit, state}, message) {
            commit; state;
            if (!Vue.prototype.$socket) {
                return
            }
            Vue.prototype.$socket.sendObj(message)
        },
        connect: function({commit, state}, host) {
            commit; state;
            if (Vue.prototype.$socket) {
                return
            }
            Vue.prototype.$connect(host, {
                format: 'json',
                reconnection: true,
                reconnectionDelay: 3000,
                store: this
            });
        },
        disconnect: function({commit, state}) {
            commit; state;
            if (!Vue.prototype.$socket) {
                return
            }
            Vue.prototype.$disconnect()
        }
    }
})

function sendHello() {
    let token = Vue.prototype.$cookies.get("TOKEN");
    if(token != null && token != undefined) {
        Vue.prototype.$socket.sendObj({
            'clazz': 'org.drasyl.all.monitoring.models.WebsocketRequest',
            'token': token,
            'action': 'getAll'});
        }
    }
