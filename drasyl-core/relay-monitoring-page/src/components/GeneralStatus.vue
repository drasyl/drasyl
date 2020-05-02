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
    <div v-if="status && configs">

        <vs-row vs-align="center" vs-type="flex" vs-justify="space-around" vs-lg="12" vs-xs="12">
            <vs-col vs-type="flex" vs-justify="center" vs-align="center" vs-lg="3" vs-xs="12" vs-sm="5">
                <apexchart type="pie" :options="chartOptions" :series="chartSeries" width="400"></apexchart>
            </vs-col>
        </vs-row>

        <vs-row vs-lg="10" vs-xs="10" vs-sm="10" vs-align="flex-end">
            <vs-col vs-lg="3" vs-sm="4" vs-xs="6">
                <vs-list-item icon="fingerprint" title="User Agent" :subtitle="status.ua"></vs-list-item>
            </vs-col>

            <vs-col vs-lg="3" vs-sm="4" vs-xs="6">
                <vs-list-item icon="vpn_key" title="System UID" :subtitle="status.systemUID"></vs-list-item>
            </vs-col>

            <vs-col vs-lg="3" vs-sm="4" vs-xs="6">
                <vs-list-item icon="signal_cellular_alt" title="System Entrypoint" :subtitle="status.ip"></vs-list-item>
            </vs-col>

            <vs-col vs-lg="3" vs-sm="4" vs-xs="6">
                <vs-list-item icon="watch_later" title="Uptime" :subtitle="uptime"></vs-list-item>
            </vs-col>

            <vs-col vs-lg="3" vs-sm="4" vs-xs="6">
                <vs-list-item icon="call_made" title="Total Sent Messages" :subtitle="numberWithCommas(status.totalSentMessages)+' ('+(status.totalSentMessages/(status.totalSentMessages+status.totalFailedMessages))*100+'%) successful / '+numberWithCommas(status.totalFailedMessages)+' ('+(status.totalFailedMessages/(status.totalSentMessages+status.totalFailedMessages))*100+
                '%) failed'"></vs-list-item>
            </vs-col>

            <vs-col vs-lg="3" vs-sm="4" vs-xs="6">
                <vs-list-item icon="call_received" title="Total Received Messages" :subtitle="numberWithCommas(status.totalReceivedMessages)"></vs-list-item>
            </vs-col>

            <vs-col vs-lg="3" vs-sm="4" vs-xs="6">
                <vs-list-item icon="sync" title="Futures" :subtitle="numberWithCommas(status.pendingFutures)+' pending / '+numberWithCommas(status.timeoutedFutures)+' timeouted'"></vs-list-item>
            </vs-col>

            <vs-col vs-lg="3" vs-sm="4" vs-xs="6">
                <vs-list-item icon="track_changes" title="Debugging" :subtitle="configs.debugging.toString()"></vs-list-item>
            </vs-col>

            <vs-col vs-lg="3" vs-sm="4" vs-xs="6">
                <vs-list-item icon="update" title="Connection Retries" :subtitle="numberWithCommas(configs.connection_tries) +' retries'"></vs-list-item>
            </vs-col>

            <vs-col vs-lg="3" vs-sm="4" vs-xs="6">
                <vs-list-item icon="grain" title="Default Channel" :subtitle="configs.default_channel"></vs-list-item>
            </vs-col>

            <vs-col vs-lg="3" vs-sm="4" vs-xs="6">
                <vs-list-item icon="swap_calls" title="Future Timeout" :subtitle="numberWithCommas(configs.default_future_timeout)"></vs-list-item>
            </vs-col>

            <vs-col vs-lg="3" vs-sm="4" vs-xs="6">
                <vs-list-item icon="schedule" title="P2P Timeout" :subtitle="numberWithCommas(configs.default_p2p_timeout)"></vs-list-item>
            </vs-col>

            <vs-col vs-lg="3" vs-sm="4" vs-xs="6">
                <vs-list-item icon="swap_horiz" title="Handoff Timeout" :subtitle="numberWithCommas(configs.max_handoff_timeout)"></vs-list-item>
            </vs-col>

            <vs-col vs-lg="3" vs-sm="4" vs-xs="6">
                <vs-list-item icon="thumbs_up_down" title="Handshake Timeout" :subtitle="numberWithCommas(configs.max-handshake-timeout)"></vs-list-item>
            </vs-col>

            <vs-col vs-lg="3" vs-sm="4" vs-xs="6">
                <vs-list-item icon="timeline" title="Max. Hop Count" :subtitle="numberWithCommas(configs.max_hop_count) +' hops'"></vs-list-item>
            </vs-col>

            <vs-col vs-lg="3" vs-sm="4" vs-xs="6">
                <vs-list-item icon="sd_storage" title="Message Bucket Limit" :subtitle="numberWithCommas(configs.msg_bucket_limit) +' elements'"></vs-list-item>
            </vs-col>

            <vs-col vs-lg="3" vs-sm="4" vs-xs="6">
                <vs-list-item icon="security" title="SSL Enabled" :subtitle="configs.ssl.enabled.toString()"></vs-list-item>
            </vs-col>

            <vs-col vs-lg="3" vs-sm="4" vs-xs="6">
                <vs-list-item icon="verified_user" title="SSL Protocols" :subtitle="configs.ssl.protocols.toString()"></vs-list-item>
            </vs-col>

            <vs-col vs-lg="3" vs-sm="4" vs-xs="6">
                <vs-list-item icon="data_usage" title="Flush Buffer Size" :subtitle="numberWithCommas(configs.flush-buffer-size)"></vs-list-item>
            </vs-col>

            <vs-col vs-lg="3" vs-sm="4" vs-xs="6">
                <vs-list-item icon="notifications_paused" title="IDLE Timeout" :subtitle="numberWithCommas(configs.idle.timeout)"></vs-list-item>
            </vs-col>

            <vs-col vs-lg="3" vs-sm="4" vs-xs="6">
                <vs-list-item icon="notifications_active" title="IDLE Retries" :subtitle="numberWithCommas(configs.idle.retries)+' retries'"></vs-list-item>
            </vs-col>

            <vs-col vs-lg="3" vs-sm="4" vs-xs="6">
                <vs-list-item icon="shutter_speed" title="Dead System Save Time"
                              :subtitle="numberWithCommas(configs.monitoring.dead_clients_saving_time)"></vs-list-item>
            </vs-col>
        </vs-row>
        <br />
        <div v-if="configs.init_peer_list.length !== 0">
            <vs-divider>Initial peers</vs-divider>
            <vs-row vs-align="center" vs-lg="12" vs-xs="12">
                <vs-col vs-justify="center" vs-align="center" vs-offset="3" vs-lg="6" vs-xs="8" vs-sm="12">
                    <vs-table search pagination :data="configs.init_peer_list">
                        <template slot="header">
                            Initial peers
                        </template>
                        <template slot="thead">
                            <vs-th>
                                IP
                            </vs-th>
                        </template>

                        <template slot-scope="{data}">
                            <vs-tr :data="tr" :key="indextr" v-for="(tr, indextr) in data" v-bind:class="{highlight: indextr%2}">
                                <vs-td :data="tr">
                                    {{tr}}
                                </vs-td>
                            </vs-tr>
                        </template>
                    </vs-table>
                </vs-col>
            </vs-row>
        </div>
    </div>
</template>


<script scoped>
    import moment from 'moment';

    export default {
    name: 'GeneralStatus',
    props: [
        'status',
        'configs'
    ],
    data() {
        return {
            chartOptions: {
                chart: {
                    id: 'totalMessagesChart'
                },
                labels: ['Successful Messages', 'Failed Messages'],
                theme: {
                    palette: 'palette5'
                }
            }
        }
    },
    computed:{
        uptime() {
            let diff = new moment.duration(this.status.bootTime);

            return diff.days()+"d "+diff.hours()+"h "+diff.minutes()+"m "+diff.seconds()+"s";
        },
        chartSeries() {
            return [this.status.totalSentMessages, this.status.totalFailedMessages];
        }
    },
    methods: {
        numberWithCommas(x) {
            return x.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");
        }
    }
}
</script>

<style scoped>
.con-vs-card {
    background: rgba(0, 150, 136, 0.2);
    text-align: center;
}
</style>
