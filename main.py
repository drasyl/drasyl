import time
import libdrasyl

def on_event(event):
    # node events
    if event.event_code == libdrasyl.DRASYL_EVENT_NODE_UP:
        print("Node `%s` started." % event.node.identity.identity_public_key)
    elif event.event_code == libdrasyl.DRASYL_EVENT_NODE_DOWN:
        print("Node `%s` is shutting down." % event.node.identity.identity_public_key)
    elif event.event_code == libdrasyl.DRASYL_EVENT_NODE_ONLINE:
        print("Node `%s` is now online." % event.node.identity.identity_public_key)
    elif event.event_code == libdrasyl.DRASYL_EVENT_NODE_OFFLINE:
        print("Node `%s` is now offline." % event.node.identity.identity_public_key)
    elif event.event_code == libdrasyl.DRASYL_EVENT_NODE_UNRECOVERABLE_ERROR:
        print("Node `%s` failed to start." % event.node.identity.identity_public_key)
    elif event.event_code == libdrasyl.DRASYL_EVENT_NODE_NORMAL_TERMINATION:
        print("Node `%s` shut down." % event.node.identity.identity_public_key)
    # peer events
    elif event.event_code == libdrasyl.DRASYL_EVENT_PEER_DIRECT:
        print("Direct connection to peer `%s`." % event.peer.address)
    elif event.event_code == libdrasyl.DRASYL_EVENT_PEER_RELAY:
        print("Relayed connection to peer `%s`." % event.peer.address)
    elif event.event_code == libdrasyl.DRASYL_EVENT_LONG_TIME_ENCRYPTION:
        print("Long time encryption to peer `%s`." % event.peer.address)
    elif event.event_code == libdrasyl.DRASYL_EVENT_PERFECT_FORWARD_SECRECY_ENCRYPTION:
        print("Perfect forward secrecy encryption to peer `%s`." % event.peer.address)
    # message events
    elif event.event_code == libdrasyl.DRASYL_EVENT_MESSAGE:
        print("Node received from peer `%s` message `%s`" % (event.message_sender, event.message_payload))
    # all other events
    elif event.event_code == libdrasyl.DRASYL_EVENT_INBOUND_EXCEPTION:
        print("Node faced error while receiving message.")
    else:
        print("Unknown event code received: %i" % event.event_code)

libdrasyl.drasyl_node_init(on_event)

identity = libdrasyl.drasyl_node_identity()
print("My address: %s" % identity.identity_public_key)

libdrasyl.drasyl_node_start()

print("Wait for node to become online...")
while not libdrasyl.drasyl_node_is_online():
    time.sleep(0.05)

recipient = "78483253e5dbbe8f401dd1bd1ef0b6f1830c46e411f611dc93a664c1e44cc054".encode("UTF-8")
payload = "hello there".encode("UTF-8")
libdrasyl.drasyl_node_send(recipient, payload)

time.sleep(10)

libdrasyl.drasyl_node_stop()
libdrasyl.drasyl_shutdown_event_loop()
libdrasyl.thread_tear_down()
