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
                        Remote Clients
                    </template>
                    <template slot="thead">
                        <vs-th>
                            ID
                        </vs-th>
                    </template>

                    <template slot-scope="{data}">
                        <vs-tr :data="tr" :key="indextr" v-for="(tr, indextr) in data" v-bind:class="{highlight: indextr%2}">
                            <vs-td :data="tr.uid">
                                {{tr.uid}}
                            </vs-td>

                            <template slot="expand">
                                <div class="con-expand-clients" v-bind:class="{highlight: indextr%2}">
                                    <vs-row vs-lg="10" vs-xs="10" vs-sm="10" vs-align="flex-end">
                                        <vs-col vs-lg="3" vs-sm="4" vs-xs="6">
                                            <vs-list>
                                                <vs-list-item v-if="tr.sessionChannels" icon="grain" title="Channels" :subtitle="tr.sessionChannels.toString()"></vs-list-item>
                                            </vs-list>
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
export default {
    name: 'RemoteTable',
    props: [
        'clients'
    ]
}
</script>
<style>
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
