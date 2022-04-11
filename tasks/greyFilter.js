((length, red, green, blue) => {
    let avg = [];

    for (let i = 0; i < length; i++) {
        avg.push(((red[i] + green[i] + blue[i]) / 3) | 0);
    }

    return avg;
});
