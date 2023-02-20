function checkprime(a) {
    let c = 2;

    while (c < (a - 1)) {
        if ((a % c) === 0) {
            return 0;
        }
        c++;
    }

    if (c === a) {
        return a;
    }
}

((low, high) => {
    while (low < high) {
        checkprime(low);
        low++;
    }
});
