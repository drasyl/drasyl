#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "./drasyl-shared-library/src/main/c/drasyl.h"
#include "libdrasyl.h"

void console_logger(graal_isolatethread_t* thread, int level, unsigned long time, char* message) {
    char level_marker[6];
    switch (level) {
        case DRASYL_LOG_TRACE:
            strcpy(level_marker, "TRACE");
            break;
        case DRASYL_LOG_DEBUG:
            strcpy(level_marker, "DEBUG");
            break;
        case DRASYL_LOG_INFO:
            strcpy(level_marker, "INFO ");
            break;
        case DRASYL_LOG_WARN:
            strcpy(level_marker, "WARN ");
            break;
        case DRASYL_LOG_ERROR:
            strcpy(level_marker, "ERROR");
        break;
            default:
            return;
    }
    printf("%ld %s : %s\n", time, level_marker, message);
}

void on_drasyl_event(graal_isolatethread_t* thread, drasyl_event_t* event) {
    switch (event->event_code) {
        case DRASYL_EVENT_NODE_UP:
            printf("Node `%.64s` started.\n", event->node->identity->identity_public_key);
            break;
        case DRASYL_EVENT_NODE_DOWN:
            printf("Node `%.64s` is shutting down.\n", event->node->identity->identity_public_key);
            break;
        case DRASYL_EVENT_NODE_ONLINE:
            printf("Node `%.64s` is now online.\n", event->node->identity->identity_public_key);
            break;
        case DRASYL_EVENT_NODE_OFFLINE:
            printf("Node `%.64s` is now offline.\n", event->node->identity->identity_public_key);
            break;
        case DRASYL_EVENT_NODE_UNRECOVERABLE_ERROR:
            printf("Node `%.64s` failed to start.\n", event->node->identity->identity_public_key);
            exit(1);
            break;
        case DRASYL_EVENT_NODE_NORMAL_TERMINATION:
            printf("Node `%.64s` shut down.\n", event->node->identity->identity_public_key);
            break;
        case DRASYL_EVENT_PEER_DIRECT:
            printf("Direct connection to peer `%.64s`.\n", event->peer->address);
            break;
        case DRASYL_EVENT_PEER_RELAY:
            printf("Relayed connection to peer `%.64s`.\n", event->peer->address);
            break;
        case DRASYL_EVENT_LONG_TIME_ENCRYPTION:
            printf("Long time encryption to peer `%.64s`.\n", event->peer->address);
            break;
        case DRASYL_EVENT_PERFECT_FORWARD_SECRECY_ENCRYPTION:
            printf("Perfect forward secrecy encryption to peer `%.64s`.\n", event->peer->address);
            break;
        case DRASYL_EVENT_MESSAGE:
            printf("Node received from peer `%.64s` message `%.*s`.\n", event->message_sender, event->message_payload_len, event->message_payload);
            break;
        case DRASYL_EVENT_INBOUND_EXCEPTION:
            printf("Node faced error while receiving message.\n");
            break;
        default:
            printf("Unknown event code received: %d\n", event->event_code);
    }
}

int main(int argc, char **argv) {
    graal_isolate_t *isolate = NULL;
    graal_isolatethread_t *thread = NULL;

    if (graal_create_isolate(NULL, &isolate, &thread) != 0) {
        fprintf(stderr, "initialization error\n");
        goto clean_up;
    }

    drasyl_set_logger(thread, &console_logger);

    int version = drasyl_node_version(thread);
    printf("drasyl node version: %i.%i.%i\n", (version >> 24) & 0xff, (version >> 16) & 0xff, (version >> 8) & 0xff);

    char config[] = "my-node.conf"; // TODO: memory never released?
    if (drasyl_node_init(thread, config, sizeof(config), &on_drasyl_event) != DRASYL_SUCCESS) {
        fprintf(stderr, "could not init node\n");
        goto clean_up;
    }

    drasyl_identity_t *identity = calloc(1, sizeof(drasyl_identity_t));
    if (drasyl_node_identity(thread, identity) != DRASYL_SUCCESS) {
        fprintf(stderr, "could not retrieve node identity\n");
        goto clean_up;
    }
    printf("My address: %.64s\n", identity->identity_public_key);

    // try to free memory
    free(identity);

    if (drasyl_node_start(thread) != DRASYL_SUCCESS) {
        fprintf(stderr, "could not start node\n");
        goto clean_up;
    }

    printf("Wait for node to become online...\n");
    while (!drasyl_node_is_online(thread)) {
        drasyl_sleep(thread, 50);
    }

    char recipient[] = "78483253e5dbbe8f401dd1bd1ef0b6f1830c46e411f611dc93a664c1e44cc054"; // TODO: memory never released?
    char payload[] = "hello there"; // TODO: memory never released?
    if (drasyl_node_send(thread, recipient, payload, sizeof(payload)) != DRASYL_SUCCESS) {
        fprintf(stderr, "could not send message\n");
        goto clean_up;
    }

    drasyl_sleep(thread, 10000);

    if (drasyl_node_stop(thread) != DRASYL_SUCCESS) {
        fprintf(stderr, "could not stop node\n");
        goto clean_up;
    }

    goto clean_up_2;

clean_up:
    if (drasyl_shutdown_event_loop(thread) != DRASYL_SUCCESS) {
        fprintf(stderr, "could not shutdown event loop\n");
        graal_tear_down_isolate(thread);

        return 1;
    }

    graal_tear_down_isolate(thread);
    return 1;
clean_up_2:
    if (drasyl_shutdown_event_loop(thread) != DRASYL_SUCCESS) {
        fprintf(stderr, "could not shutdown event loop\n");
        graal_tear_down_isolate(thread);

        return 1;
    }

    graal_tear_down_isolate(thread);
    return 0;
 }
