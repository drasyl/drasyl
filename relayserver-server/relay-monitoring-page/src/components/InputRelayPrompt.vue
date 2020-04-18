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
    <div>
        <vs-prompt
                color="primary"
                @cancel="valMultipe.host='',valMultipe.token='',close()"
                @accept="acceptPrompt"
                @close="close"
                :is-valid="validName"
                :active.sync="isPrompt" title="Relay Connection Settings">
        <div class="con-prompt">
            Enter the Host URL of the Relay and the Access Token to <b>continue</b>.
            <vs-input placeholder="Host URL" v-model="valMultipe.host" type="url"/>
            <vs-input placeholder="Access Token" v-model="valMultipe.token"/>

            <br />
            <vs-alert :active="!validName" color="danger" icon="new_releases">
                Fields can not be empty and the host URL must be valid. Please enter the data.
            </vs-alert>
        </div>
    </vs-prompt>
</div>
</template>

<script>
export default {
    props: {
        relayConnectionPrompt: {
            type: Boolean,
            default() {
                return false;
            }
        }
    },
    data(){
        return {
            valMultipe:{
                host:'',
                token:''
            },
        }
    },
    computed: {
        validName(){
            return (this.valMultipe.host.length > 0 && this.valMultipe.token.length > 0) && this.isValidURL(this.valMultipe.host)
        },
        isPrompt: {
            get: function() {
                return this.relayConnectionPrompt;
            },
            set: function(val) {
                this.prompt(val);
            }
        },
    },
    methods:{
        acceptPrompt(){
            this.$vs.notify({
                color:'success',
                title:'Saved settings',
                text:'Saved relay connection settings'
            });

            this.$cookies.set('HOST', this.valMultipe.host);
            this.$cookies.set('TOKEN', this.valMultipe.token);
        },
        close(){
            this.$vs.notify({
                color:'danger',
                title:'Closed',
                text:'You have aborted the initialization of the connection to the relay!'
            })
        },
        prompt(val = true) {
            this.$emit('update-prompt', val);
        },
        isValidURL(url) {
            try {
                new URL(url);
                return true;
            } catch(_) {
                return false;
            }
        }
    }
}
</script>

<style scoped>
.con-prompt {
    padding: 10px;
    padding-bottom: 0px;
}

.vs-input {
    width: 100%;
    margin-top: 10px;
}

.vs-dialog .vs-button {
    margin: 1em 1em 1em 0;
}

.token-btn {
    margin-bottom: 2em;
}
</style>
