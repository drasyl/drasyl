/*
 * Copyright (c) 2020
 *
 * This file is part of Relayserver.
 *
 * Relayserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Relayserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Relayserver.  If not, see <http://www.gnu.org/licenses/>.
 */

/*jshint esversion: 6 */
import Vue from 'vue';
import App from './App.vue';
import VueApexCharts from 'vue-apexcharts';
import Vuesax from 'vuesax';
import VueCookies from 'vue-cookies';
import axios from 'axios';
import VueAxios from 'vue-axios';
import 'vuesax/dist/vuesax.css';
import 'material-icons/iconfont/material-icons.css';
import VueNativeSock from 'vue-native-websocket';
import { store } from './store/store';

Vue.use(VueNativeSock, 'ws://localhost:8080', {
  connectManually: true,
});

Vue.use(Vuesax);
Vue.use(VueCookies);
Vue.use(VueAxios, axios);
Vue.component('apexchart', VueApexCharts);

Vue.config.productionTip = false;

Vue.$cookies.config('30d');

new Vue({
    render: h => h(App),
    store
}).$mount('#app');
