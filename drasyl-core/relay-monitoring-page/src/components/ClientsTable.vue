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
    <div v-if="clients">
        <vs-row vs-align="center" vs-justify="space-around" vs-lg="12" vs-xs="12">
            <vs-col vs-justify="center" vs-align="center" vs-lg="11" vs-xs="11" vs-sm="11">
                <vs-table search pagination :data="clients">
                    <template slot="header">
                        {{ tableTitle }}
                    </template>
                    <template slot="thead">
                        <vs-th>
                            ID
                        </vs-th>
                        <vs-th>
                            IP
                        </vs-th>
                        <vs-th>
                            Initialized
                        </vs-th>
                    </template>

                    <template slot-scope="{data}">
                        <vs-tr :data="tr" :key="indextr" v-for="(tr, indextr) in data" v-bind:class="{highlight: indextr%2}">
                            <vs-td :data="tr.uid">
                                {{tr.uid}}
                            </vs-td>

                            <vs-td :data="tr.ip">
                                {{tr.ip}}
                            </vs-td>

                            <vs-td :data="tr.initialized">
                                {{tr.initialized}}
                            </vs-td>

                            <template slot="expand">
                                <div class="con-expand-clients" v-bind:class="{highlight: indextr%2}">
                                    <vs-row vs-lg="10" vs-xs="10" vs-sm="10" vs-align="flex-end">
                                        <vs-col vs-lg="3" vs-sm="4" vs-xs="6">
                                            <vs-list-item icon="watch_later" title="Uptime" :subtitle="msToString(tr.bootTime)"></vs-list-item>
                                        </vs-col>
                                        <vs-col vs-lg="3" vs-sm="4" vs-xs="6">
                                            <vs-list-item icon="stop" title="Terminated" :subtitle="tr.terminated.toString()"></vs-list-item>
                                        </vs-col>

                                        <vs-col vs-lg="3" vs-sm="4" vs-xs="6">
                                            <vs-list-item icon="call_made" title="Total Sent Messages" :subtitle="numberWithCommas(tr.totalSentMessages)+' ('+(tr.totalSentMessages/(tr.totalSentMessages+tr.totalFailedMessages))*100+'%) successful / '+numberWithCommas(tr.totalFailedMessages)+' ('+(tr.totalFailedMessages/(tr.totalSentMessages+tr.totalFailedMessages))*100+
                                            '%) failed'"></vs-list-item>
                                        </vs-col>
                                        <vs-col vs-lg="3" vs-sm="4" vs-xs="6">
                                            <vs-list-item icon="call_received" title="Total Received Messages"
                                                          :subtitle="tr.totalReceivedMessages.toString()"></vs-list-item>
                                        </vs-col>
                                        <vs-col vs-lg="3" vs-sm="4" vs-xs="6">
                                            <vs-list-item v-if="tr.channels.length !== 0" icon="grain" title="Channels" :subtitle="tr.channels.toString()"></vs-list-item>
                                            <vs-list-item v-else icon="grain" title="Channels" subtitle="N/A"></vs-list-item>
                                        </vs-col>
                                        <vs-col vs-lg="3" vs-sm="4" vs-xs="6">
                                            <vs-list-item icon="sync" title="Pending Futures" :subtitle="numberWithCommas(tr.pendingFutures)"></vs-list-item>
                                        </vs-col>
                                        <vs-col vs-lg="3" vs-sm="4" vs-xs="6">
                                            <vs-list-item icon="sync_disabled" title="Time Outed Futures"
                                                          :subtitle="numberWithCommas(tr.timeoutedFutures)"></vs-list-item>
                                        </vs-col>
                                        <vs-col vs-lg="3" vs-sm="4" vs-xs="6">
                                            <vs-list-item v-if="tr.ua" icon="fingerprint" title="User Agent" :subtitle="tr.ua.toString()"></vs-list-item>
                                            <vs-list-item v-else icon="fingerprint" title="User Agent" subtitle="N/A"></vs-list-item>
                                        </vs-col>
                                    </vs-row>
                                </div>
                            </template>
                        </vs-tr>
                    </template>
                </vs-table>
            </vs-col>
        </vs-row>
    </div>
</template>

<script>
    import moment from 'moment';

    export default {
    name: 'ClientsTable',
    props: [
        'clients',
        'tableTitle'
    ],
    methods: {
        msToString(ms=0) {
            let diff = new moment.duration(ms);

            return diff.days()+"d "+diff.hours()+"h "+diff.minutes()+"m "+diff.seconds()+"s";
        },
        numberWithCommas(x) {
            return x.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");
        }
    }
}
</script>
<style scoped>
.con-expand-clients {
    display: flex;
    padding: 10px;
    padding-bottom: 0px;
    align-items: center;
    justify-content: space-between;
}

.highlight {
    background: #e0f2f1 !important;
}

.vs-table--thead tr {
    background: #80cbc4 !important;
}

.vs-table--header {
    font-size: 	1.5em;
    font-weight: bold;
}

.content-tr-expand .highlight {
    box-shadow: 0 0 0 10000px #e0f2f1;
}
</style>
