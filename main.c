#include <stdio.h>
#include <stdlib.h>

#include "libdrasyl.h"

void on_drasyl_event(int evtInt) {
    printf("evtInt = %d\n", evtInt);
}

int main(int argc, char **argv) {
    if (argc != 2) {
        fprintf(stderr, "Usage: %s <filter>\n", argv[0]);
        exit(1);
    }

    graal_isolate_t *isolate = NULL;
    graal_isolatethread_t *thread = NULL;

    if (graal_create_isolate(NULL, &isolate, &thread) != 0) {
        fprintf(stderr, "initialization error\n");
        return 1;
    }

//    if (drasyl_node_set_event_handler(thread, &on_drasyl_event) != 0) {
//        fprintf(stderr, "could not set event handler\n");
//        return 1;
//    }

    if (drasyl_node_start(thread) != 0) {
        fprintf(stderr, "could not start node\n");
        return 1;
    }

//    printf("Wait for node to become online...");
//    while (!drasyl_node_is_online(thread)) {
//        drasyl_util_delay(thread, 50);
//    }
//    printf("online!\n");
//
//    if (drasyl_node_send(thread, "78483253e5dbbe8f401dd1bd1ef0b6f1830c46e411f611dc93a664c1e44cc054", "huhu") != 0) {
//        fprintf(stderr, "could not send message\n");
//        return 1;
//    }

    if (drasyl_node_stop(thread) != 0) {
        fprintf(stderr, "could not stop node\n");
        return 1;
    }
//
//    if (drasyl_shutdown_event_loop(thread) != 0) {
//        fprintf(stderr, "could not shutdown event loop\n");
//        return 1;
//    }

    graal_tear_down_isolate(thread);
 }
