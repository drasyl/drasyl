import drasyl
import pytz
from datetime import datetime
import time

def console_logger(level, time, message):
    if level == drasyl.DRASYL_LOG_INFO:
        level_marker = "INFO"
    elif level == drasyl.DRASYL_LOG_WARN:
        level_marker = "WARN"
    elif level == drasyl.DRASYL_LOG_ERROR:
        level_marker = "ERROR"
    else:
        return

    print("%-5s %-32s %s" % (level_marker, datetime.fromtimestamp(time / 1000.0, pytz.timezone("Europe/Berlin")).isoformat(), message.decode("UTF-8"))),

drasyl.drasyl_set_logger(console_logger)

print("drasyl node version: %s" % drasyl.drasyl_node_version())

def on_event(event):
    # node events
    if event.event_code == drasyl.DRASYL_EVENT_NODE_UP:
        print("Node `%s` started." % event.node.identity.identity_public_key)
    elif event.event_code == drasyl.DRASYL_EVENT_NODE_DOWN:
        print("Node `%s` is shutting down." % event.node.identity.identity_public_key)
    elif event.event_code == drasyl.DRASYL_EVENT_NODE_ONLINE:
        print("Node `%s` is now online." % event.node.identity.identity_public_key)
    elif event.event_code == drasyl.DRASYL_EVENT_NODE_OFFLINE:
        print("Node `%s` is now offline." % event.node.identity.identity_public_key)
    elif event.event_code == drasyl.DRASYL_EVENT_NODE_UNRECOVERABLE_ERROR:
        print("Node `%s` failed to start." % event.node.identity.identity_public_key)
    elif event.event_code == drasyl.DRASYL_EVENT_NODE_NORMAL_TERMINATION:
        print("Node `%s` shut down." % event.node.identity.identity_public_key)
    # peer events
    elif event.event_code == drasyl.DRASYL_EVENT_PEER_DIRECT:
        print("Direct connection to peer `%s`." % event.peer.address)
    elif event.event_code == drasyl.DRASYL_EVENT_PEER_RELAY:
        print("Relayed connection to peer `%s`." % event.peer.address)
    elif event.event_code == drasyl.DRASYL_EVENT_LONG_TIME_ENCRYPTION:
        print("Long time encryption to peer `%s`." % event.peer.address)
    elif event.event_code == drasyl.DRASYL_EVENT_PERFECT_FORWARD_SECRECY_ENCRYPTION:
        print("Perfect forward secrecy encryption to peer `%s`." % event.peer.address)
    # message events
    elif event.event_code == drasyl.DRASYL_EVENT_MESSAGE:
        print("Node received from peer `%s` message `%s`" % (event.message_sender, event.message_payload))
    # all other events
    elif event.event_code == drasyl.DRASYL_EVENT_INBOUND_EXCEPTION:
        print("Node faced error while receiving message.")
    else:
        print("Unknown event code received: %i" % event.event_code)

#drasyl.drasyl_node_init("/Users/heiko/Development/drasyl/my-node.conf", on_event)
drasyl.drasyl_node_init(None, on_event)

identity = drasyl.drasyl_node_identity()
print("My address: %s" % identity.identity_public_key)

drasyl.drasyl_node_start()

print("Wait for node to become online...")
while not drasyl.drasyl_node_is_online():
    time.sleep(0.05)

recipient = "78483253e5dbbe8f401dd1bd1ef0b6f1830c46e411f611dc93a664c1e44cc054".encode("UTF-8")
payload = "hello there".encode("UTF-8")
drasyl.drasyl_node_send(recipient, payload)

time.sleep(10)

drasyl.drasyl_node_stop()
drasyl.drasyl_shutdown_event_loop()
drasyl.thread_tear_down()
