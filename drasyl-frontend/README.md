# relay-monitoring-page

A monitoring website is by default deactivated and listening on port `8080`. Remember to change the
 `relay.monitoring.token` otherwise the relay server is **vulnerable**.

*You should also think about opening the port for monitoring only in the local area network or for a specific address range to prevent input fuzzing attacks.*

## Project setup
```
npm install
```

### Compiles and hot-reloads for development
```
npm run serve
```

### Compiles and minifies for production
```
npm run-script build
```

### Lints and fixes files
```
npm run lint
```
