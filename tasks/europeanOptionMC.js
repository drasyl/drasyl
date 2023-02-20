((nSimulations, nSteps) => {
    var S = 18.2;
    var K = 18;
    var r = 0.0001;
    var sigma = 0.34;
    var maturity = 3;
    var nSteps = 100;
    var dt = (maturity * 1.0 / (nSteps - 1));
    var T = maturity;
    var call = 1;
    var split = 1;
    var nSimulationsF = nSimulations / split;

    function uniformInterval(a, b) {
        return a + Math.random() * (b - a);
    }

    function marsagliaPolar() {
        var x = uniformInterval(-1.0, 1.0);
        var y = uniformInterval(-1.0, 1.0);
        var d = x * x + y * y;
        var n = d;

        while (n >= 0.0) {
            if (n < 1.0) {
                if (n > 0.0) {
                    n = -1.0;
                }
            }

            if (n > 0.0) {
                var x = uniformInterval(-1.0, 1.0);
                var y = uniformInterval(-1.0, 1.0);
                var d = x * x + y * y;
                var n = d;
            }
        }

        return x * Math.sqrt(-2.0 * Math.log(d) / d);
    }

    function optionPricing() {
        var price = 0.0;
        var matrixSize = nSimulations * nSteps;
        var randmatSize = nSimulations * (nSteps - 1);
        var vsqrdt = sigma * Math.sqrt(dt);
        var drift = (r - sigma * sigma / 2.0) * dt;

        var smat = new Array(matrixSize);
        var randmat = new Array(randmatSize);
        var pvec = new Array(nSimulations);
        var i = 0;
        var j = 0;
        while (i < nSimulations) {
            smat[i * nSteps] = S;
            j = 0;
            while (j < nSteps - 1) {
                randmat[i * (nSteps - 1) + j] = marsagliaPolar();
                j++;
            }
            i++;
        }

        i = 0;
        while (i < nSimulations) {
            j = 1;
            while (j < nSteps) {
                smat[i * nSteps + j] = Math.exp(drift + vsqrdt * randmat[i * (nSteps - 1) + j - 1]);
                j++;
            }
            i++;
        }

        i = 0;
        var temp = 0.0;
        while (i < nSimulations) {
            j = nSteps - 1;
            while (j > 0) {
                temp = smat[nSteps * i];
                k = 1;
                while (k <= j) {
                    temp = temp * smat[i * nSteps + k];
                    k++;
                }
                smat[i * nSteps + j] = temp
                j--;
            }
            i++;
        }

        var value = 0.0;
        i = 0;
        while (i < nSimulations) {
            if (call == 1) {
                if (smat[i * nSteps + (nSteps - 1)] > K) {
                    pvec[i] = smat[i * nSteps + (nSteps - 1)] - K;
                }
                else {
                    pvec[i] = 0.0;
                }
            }
            else {
                if (K - (smat[i * nSteps + (nSteps - 1)]) >= 0.0) {
                    pvec[i] = K - smat[i * nSteps + (nSteps - 1)];
                }
                else {
                    pvec[i] = 0.0;
                }
            }
            value = value + pvec[i];
            i++;
        }
        value = (value / nSimulationsF) * Math.exp(-r * T);
        return value;
    }

    return [optionPricing()];
});
