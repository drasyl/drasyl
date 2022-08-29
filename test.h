typedef enum {
    DRASYL_NODE_EVENT_NODE_UP = 100,
    DRASYL_NODE_EVENT_NODE_DOWN = 101,
    DRASYL_NODE_EVENT_NODE_ONLINE = 102,
    DRASYL_NODE_EVENT_NODE_OFFLINE = 103,
    DRASYL_NODE_EVENT_NODE_UNRECOVERABLE_ERROR = 104,
    DRASYL_NODE_EVENT_NODE_NORMAL_TERMINATION = 105,
    DRASYL_NODE_EVENT_PEER_DIRECT = 200,
    DRASYL_NODE_EVENT_PEER_RELAY = 201,
    DRASYL_NODE_EVENT_LONG_TIME_ENCRYPTION = 202,
    DRASYL_NODE_EVENT_PERFECT_FORWARD_SECRECY_ENCRYPTION = 203,
    DRASYL_NODE_EVENT_MESSAGE = 300,
    DRASYL_NODE_EVENT_INBOUND_EXCEPTION = 400
} drasyl_node_event_code;

typedef struct {
    int proof_of_work;
    char identity_public_key[64];
    char identity_secret_key[128];
} drasyl_identity;

typedef struct {
    char address[64];
} drasyl_node;

typedef struct {
    char address[64];
} drasyl_peer;

typedef struct {
    /**
     * Event identifier
     */
    int32_t event_code;
    drasyl_node* node;
    drasyl_peer* peer;
    char message_sender[64];
    int32_t message_payload_len;
    char* message_payload;
} drasyl_node_event;
