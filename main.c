#include <stdio.h>
#include <stdlib.h>

#include "libdrasyl.h"
#include "test.h"

void on_drasyl_event(graal_isolatethread_t* thread, drasyl_node_event* event) {
    switch (event->event_code) {
        case DRASYL_NODE_EVENT_NODE_UP:
            printf("Node `%.64s` started \n", event->node->address);
            break;
        case DRASYL_NODE_EVENT_NODE_DOWN:
            printf("Node `%.64s` is shutting down\n", event->node->address);
            break;
        case DRASYL_NODE_EVENT_NODE_ONLINE:
            printf("Node `%.64s` is now online\n", event->node->address);
            break;
        case DRASYL_NODE_EVENT_NODE_OFFLINE:
            printf("Node `%.64s` is now offline\n", event->node->address);
            break;
        case DRASYL_NODE_EVENT_NODE_UNRECOVERABLE_ERROR:
            printf("Node `%.64s` failed to start\n", event->node->address);
            exit(1);
            break;
        case DRASYL_NODE_EVENT_NODE_NORMAL_TERMINATION:
            printf("Node `%.64s` shut down\n", event->node->address);
            break;
        case DRASYL_NODE_EVENT_PEER_DIRECT:
            printf("Node has direct connection to peer `%.64s`\n", event->peer->address);
            break;
        case DRASYL_NODE_EVENT_PEER_RELAY:
            printf("Node has relayed connection to peer `%.64s`\n", event->peer->address);
            break;
        case DRASYL_NODE_EVENT_LONG_TIME_ENCRYPTION:
            printf("Node has long time encryption to peer `%.64s`\n", event->peer->address);
            break;
        case DRASYL_NODE_EVENT_PERFECT_FORWARD_SECRECY_ENCRYPTION:
            printf("Node has perfect forward secrecy encryption to peer `%.64s`\n", event->peer->address);
            break;
        case DRASYL_NODE_EVENT_MESSAGE:
            printf("Node received message `%.*s` from `%.64s`: `%d`\n", event->message_payload_len, event->message_payload, event->message_sender, event->message_payload_len);
            break;
        case DRASYL_NODE_EVENT_INBOUND_EXCEPTION:
            printf("Node faced error while receiving message\n");
            break;
        default:
            printf("event->event_code = %d\n", event->event_code);
    }
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
        goto clean_up;
    }

    if (drasyl_node_set_event_handler(thread, &on_drasyl_event) != 0) {
        fprintf(stderr, "could not set event handler\n");
        goto clean_up;
    }

    if (drasyl_node_start(thread) != 0) {
        fprintf(stderr, "could not start node\n");
        goto clean_up;
    }

    printf("Wait for node to become online...\n");
    while (!drasyl_node_is_online(thread)) {
        drasyl_util_delay(thread, 50);
    }
    printf("online!\n");

    char payload[] = "hello there";
    if (drasyl_node_send(thread, "78483253e5dbbe8f401dd1bd1ef0b6f1830c46e411f611dc93a664c1e44cc054", payload, sizeof(payload)) != 0) {
        fprintf(stderr, "could not send message\n");
        goto clean_up;
    }

    drasyl_util_delay(thread, 10000);

    if (drasyl_node_stop(thread) != 0) {
        fprintf(stderr, "could not stop node\n");
        goto clean_up;
    }

    if (drasyl_shutdown_event_loop(thread) != 0) {
        fprintf(stderr, "could not shutdown event loop\n");
        goto clean_up;
    }
    
    goto clean_up_2;
    
clean_up:
    graal_tear_down_isolate(thread);
    return 1;
clean_up_2:
    graal_tear_down_isolate(thread);
    return 0;
 }
