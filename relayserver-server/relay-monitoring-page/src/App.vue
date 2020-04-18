<!--
  - Copyright (c) 2020
  -
  - This file is part of Relayserver.
  -
  - Relayserver is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Lesser General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - Relayserver is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Lesser General Public License for more details.
  -
  - You should have received a copy of the GNU Lesser General Public License
  - along with Relayserver.  If not, see <http://www.gnu.org/licenses/>.
  -->

<template>
    <div id="app">
        <vs-row vs-w="12" class="token-btn">
            <vs-col vs-w="2" vs-offset="1">
                <vs-button color="primary" type="relief" @click="updatePrompt()">Set Token</vs-button>
            </vs-col>
            <vs-col vs-w="2" vs-offset="7">
                <vs-button radius color="success" type="flat" icon="replay" @click="send()"></vs-button>
            </vs-col>
        </vs-row>
        <div>
            <br />
            <InputRelayPrompt :relayConnectionPrompt="relayConnectionPrompt" @update-prompt="updatePrompt"></InputRelayPrompt>
        </div>
        <div v-if="isInitialised">
            <vs-tabs alignment="center" color="rgba(0, 150, 136)">
                <vs-tab label="Server Config" icon="settings_applications">
                    <div class="con-tab-ejemplo">
                        <GeneralStatus :status="status" :configs="configs"/>
                    </div>
                </vs-tab>
                <vs-tab label="Local Systems" icon="portable_wifi_off" :disabled="status.localClients.length === 0">
                    <div class="con-tab-ejemplo">
                        <ClientsTable :clients="status.localClients" tableTitle="Local Systems"
                                      v-if="status.localClients.length !== 0"></ClientsTable>
                        <h3 v-else>There are currently no local systems available.</h3>
                    </div>
                </vs-tab>
                <vs-tab label="Dead Systems" icon="wifi_tethering" :disabled="status.deadClients.length === 0">
                    <div class="con-tab-ejemplo">
                        <DeadClientsTable :clients="status.deadClients" v-if="status.deadClients.length !== 0"></DeadClientsTable>
                    </div>
                </vs-tab>
                <vs-tab label="Remote Systems" icon="wifi_tethering" :disabled="status.remoteClients.length === 0">
                    <div class="con-tab-ejemplo">
                        <RemoteTable :clients="status.remoteClients" v-if="status.remoteClients.length !== 0"></RemoteTable>
                    </div>
                </vs-tab>
                <vs-tab label="P2P Connections" icon="language" :disabled="status.relays.length === 0">
                    <div class="con-tab-ejemplo">
                        <ClientsTable :clients="status.relays" tableTitle="P2P Relay Connections"
                        v-if="status.relays.length !== 0"></ClientsTable>
                        <h3 v-else>There are currently no P2P relay connections available.</h3>
                    </div>
                </vs-tab>
            </vs-tabs>
        </div>
    </div>
</template>

<script>
    import store from './store/store.js'
    import GeneralStatus from './components/GeneralStatus.vue'
    import InputRelayPrompt from './components/InputRelayPrompt.vue'
    import ClientsTable from './components/ClientsTable.vue'
    import RemoteTable from './components/RemoteTable.vue'
    import DeadClientsTable from './components/DeadClientsTable.vue'

    export default {
    name: 'app',
    components: {
        GeneralStatus,
        InputRelayPrompt,
        ClientsTable,
        RemoteTable,
        DeadClientsTable
    },
    data(){
        return {
            relayConnectionPrompt:false
        }
    },
    mounted() {
        this.initSettings();
        this.connect();
        this.send();
    },
    methods: {
        initSettings() {
            if(this.cookiesSet()) {
                this.relayConnectionPrompt = true;
            }
        },
        updatePrompt(val = true) {
            this.relayConnectionPrompt = val;
            this.connect();
        },
        send(){
            if(!this.cookiesSet()) {
                let token = this.$cookies.get("TOKEN");
                store.dispatch('sendMessage', {
                    'clazz': 'city.sane.relay.server.monitoring.models.WebsocketRequest',
                    'token': token,
                    'action': 'getAll'});
                }
            },
            connect() {
                let vue = this;

                if(!vue.cookiesSet()) {
                    let host = vue.$cookies.get("HOST");
                    store.dispatch('connect', host);
                }
            },
            cookiesSet() {
                let token = this.$cookies.get("TOKEN");
                let host = this.$cookies.get("HOST");

                return token === null || token === undefined || host === null || host === undefined;
            }
        },
        computed: {
            connectionInfosAvailable() {
                return  this.isInitialised &&
                (this.status.relays.length !== 0 ||
                    this.status.remoteClients.length !== 0 ||
                    this.status.localClients.length !== 0);
                },
                isInitialised() {
                    return this.status.relays !== undefined &&
                    this.status.remoteClients !== undefined &&
                    this.status.localClients !== undefined;
                },
                status() {
                    return store.state.socket.status;
                },
                configs() {
                    return JSON.parse(store.state.socket.status.configs);
                }
            }
        }
        </script>

        <style>
            @import url('https://fonts.googleapis.com/css?family=Open+Sans&display=swap');
            @import url('https://fonts.googleapis.com/css?family=Montserrat&display=swap');

            body {
                background: rgba(97, 97, 97, 0.1);
            }

            .vs-con-table {
                background: transparent !important;
            }

            * {
                font-family: 'Montserrat', 'Open Sans';
                font-size: 13pt;
                -webkit-font-smoothing: antialiased;
                -moz-osx-font-smoothing: grayscale;
            }

            #app {
                font-family: 'Montserrat', 'Open Sans';
                -webkit-font-smoothing: antialiased;
                -moz-osx-font-smoothing: grayscale;
                color: #2c3e50;
                margin-top: 60px;
            }
        </style>
