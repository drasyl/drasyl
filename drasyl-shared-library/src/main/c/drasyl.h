/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
#include <stdint.h>

#define IDENTITY_PUBLIC_KEY_LENGTH_AS_STRING    64
#define IDENTITY_PRIVATE_KEY_LENGTH_AS_STRING   128

typedef enum {
    DRASYL_LOG_TRACE = 300,
    DRASYL_LOG_DEBUG = 500,
    DRASYL_LOG_INFO = 800,
    DRASYL_LOG_WARN = 900,
    DRASYL_LOG_ERROR = 1000
} drasyl_log_level_t;

typedef enum {
    /** No error */
    DRASYL_SUCCESS = 0,
    /** General error */
    DRASYL_ERROR_GENERAL = -1
} drasyl_error_code_t;

typedef enum {
    /** Signals that the node has been started */
    DRASYL_EVENT_NODE_UP = 10,
    /** Signals that the node is shut down */
    DRASYL_EVENT_NODE_DOWN = 11,
    /** Signals that the node is currently connected to a super peer */
    DRASYL_EVENT_NODE_ONLINE = 12,
    /** Signals that the node is currently not connected to a super peer */
    DRASYL_EVENT_NODE_OFFLINE = 13,
    /** Signals that the node encountered an unrecoverable error */
    DRASYL_EVENT_NODE_UNRECOVERABLE_ERROR = 14,
    /** Signals that the node has terminated normally */
    DRASYL_EVENT_NODE_NORMAL_TERMINATION = 15,
    /** Signals that the node has established a direct connection to a peer */
    DRASYL_EVENT_PEER_DIRECT = 20,
    /** Signals that communication with this peer is only possible by relaying messages via a super peer */
    DRASYL_EVENT_PEER_RELAY = 21,
    /** Signals that currently all messages from and to the peer are encrypted with a long time key */
    DRASYL_EVENT_LONG_TIME_ENCRYPTION = 22,
    /** Signals that currently all messages from and to the {@code #peer} are encrypted with an ephemeral session key */
    DRASYL_EVENT_PERFECT_FORWARD_SECRECY_ENCRYPTION = 23,
    /** Signals that the node has received a message addressed to it */
    DRASYL_EVENT_MESSAGE = 30,
    /** Signals that the node was unable to process an inbound message */
    DRASYL_EVENT_INBOUND_EXCEPTION = 40
} drasyl_event_code_t;

/** Represents the private identity of the local node node (includes the proof of work, the public, and secret key). Should be kept secret! */
typedef struct {
    /** Proof of work */
    int proof_of_work;
    /** Public Key */
    char identity_public_key[IDENTITY_PUBLIC_KEY_LENGTH_AS_STRING];
    /** Secret Key */
    char identity_secret_key[IDENTITY_PRIVATE_KEY_LENGTH_AS_STRING];
} drasyl_identity_t;

/** Used by drasyl_event_t to describe an event related to the local node */
typedef struct {
    /** Node's identity */
    drasyl_identity_t* identity;
} drasyl_node_t;

/** Used by drasyl_event_t to describe an event related to a peer */
typedef struct {
    /** Peer's address */
    char address[IDENTITY_PUBLIC_KEY_LENGTH_AS_STRING];
} drasyl_peer_t;

/** Callback event */
typedef struct {
    /** Event identifier */
    uint8_t event_code;
    /** Node this event belongs to (only present for node events) */
    drasyl_node_t* node;
    /** Peer this event belongs to (only present for peer events) */
    drasyl_peer_t* peer;
    /** Sender the message was sent from (only present for message events) */
    char message_sender[IDENTITY_PUBLIC_KEY_LENGTH_AS_STRING];
    /** Length of the message payload (only present for message events) */
    uint16_t message_payload_len;
    /** Message payload (only present for message events) */
    char* message_payload;
} drasyl_event_t;
