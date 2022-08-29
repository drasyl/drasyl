typedef enum {
    /**
     * Signals that the node has been started
     */
    DRASYL_NODE_EVENT_NODE_UP = 100,

    /**
     * Signals that the node is shut down
     */
    DRASYL_NODE_EVENT_NODE_DOWN = 101,

    /**
     * Signals that the node is currently connected to a super peer
     */
    DRASYL_NODE_EVENT_NODE_ONLINE = 102,

    /**
     * Signals that the node is currently not connected to a super peer
     */
    DRASYL_NODE_EVENT_NODE_OFFLINE = 103,

    /**
     * Signals that the node encountered an unrecoverable error
     */
    DRASYL_NODE_EVENT_NODE_UNRECOVERABLE_ERROR = 104,

    /**
     * Signals that the node has terminated normally
     */
    DRASYL_NODE_EVENT_NODE_NORMAL_TERMINATION = 105,

    /**
     * Signals that the node has established a direct connection to a peer
     */
    DRASYL_NODE_EVENT_PEER_DIRECT = 200,

    /**
     * Signals that communication with this peer is only possible by relaying messages via a super peer
     */
    DRASYL_NODE_EVENT_PEER_RELAY = 201,

    /**
     * Signals that currently all messages from and to the peer are encrypted with a long time key
     */
    DRASYL_NODE_EVENT_LONG_TIME_ENCRYPTION = 202,

    /**
     * Signals that currently all messages from and to the {@code #peer} are encrypted with an ephemeral session key.
     */
    DRASYL_NODE_EVENT_PERFECT_FORWARD_SECRECY_ENCRYPTION = 203,

    /**
     * Signals that the node has received a message addressed to it.
     */
    DRASYL_NODE_EVENT_MESSAGE = 300,

    /**
     * Signals that the node was unable to process an inbound message.
     */
    DRASYL_NODE_EVENT_INBOUND_EXCEPTION = 400
} drasyl_node_event_code;

typedef struct {
    int proof_of_work;
    char identity_public_key[64];
    char identity_secret_key[128];
} drasyl_identity;

typedef struct {
    drasyl_identity* identity;
} drasyl_node;

typedef struct {
    char address[64];
} drasyl_peer;

typedef struct {
    /**
     * Event identifier
     */
    int32_t event_code;

    /**
     * Node this event belongs to (only present for node events)
     */
    drasyl_node* node;

    /**
     * Peer this event belongs to (only present for peer events)
     */
    drasyl_peer* peer;

    /**
     * Sender the message was sent from (only present for message events)
     */
    char message_sender[64];

    /**
     * Length of the message payload (only present for message events)
     */
    int32_t message_payload_len;

    /**
     * Message payload (only present for message events)
     */
    char* message_payload;
} drasyl_node_event;
