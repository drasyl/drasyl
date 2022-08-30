from ctypes import *

libdrasyl = cdll.LoadLibrary("libdrasyl.dylib")

class drasyl_identity_t(Structure):
    _fields_ = [
        ("proof_of_work", c_int),
        ("identity_public_key", c_char * 64),
        ("identity_secret_key", c_char * 128)
    ]

class drasyl_node_t(Structure):
    _fields_ = [
        ("identity", POINTER(drasyl_identity_t))
    ]

class drasyl_peer_t(Structure):
    _fields_ = [
        ("address", c_char * 64)
    ]

class drasyl_event_t(Structure):
    _fields_ = [
        ("event_code", c_uint8),
        ("node", POINTER(drasyl_node_t)),
        ("peer", POINTER(drasyl_peer_t)),
        ("message_sender", c_char * 64),
        ("message_payload_len", c_uint16),
        ("message_payload", c_char_p)
    ]

thread = c_void_p()
if libdrasyl.graal_create_isolate(None, None, byref(thread)) != 0:
    print("initialization error")

def on_drasyl_event(thread, event):
    # node events
    if event[0].event_code == 10:
        print("Node `%s` started." % event[0].node[0].identity[0].identity_public_key.decode('UTF-8'))
    elif event[0].event_code == 11:
        print("Node `%s` is shutting down." % event[0].node[0].identity[0].identity_public_key.decode('UTF-8'))
    elif event[0].event_code == 12:
        print("Node `%s` is now online." % event[0].node[0].identity[0].identity_public_key.decode('UTF-8'))
    elif event[0].event_code == 13:
        print("Node `%s` is now offline." % event[0].node[0].identity[0].identity_public_key.decode('UTF-8'))
    elif event[0].event_code == 14:
        print("Node `%s` failed to start." % event[0].node[0].identity[0].identity_public_key.decode('UTF-8'))
    elif event[0].event_code == 15:
        print("Node `%s` shut down." % event[0].node[0].identity[0].identity_public_key.decode('UTF-8'))
    # peer events
    elif event[0].event_code == 20:
        print("Direct connection to peer `%s`." % event[0].peer[0].address.decode('UTF-8'))
    elif event[0].event_code == 21:
        print("Relayed connection to peer `%s`." % event[0].peer[0].address.decode('UTF-8'))
    elif event[0].event_code == 22:
        print("Long time encryption to peer `%s`." % event[0].peer[0].address.decode('UTF-8'))
    elif event[0].event_code == 23:
        print("Perfect forward secrecy encryption to peer `%s`." % event[0].peer[0].address.decode('UTF-8'))
    # message events
    elif event[0].event_code == 30:
        print("Node received from peer `%s` message `%s`" % (event[0].message_sender.decode('UTF-8'), event[0].message_payload.decode('UTF-8')))
    # all other events
    elif event[0].event_code == 40:
        print("Node faced error while receiving message.")
    else:
        print("Unknown event code received: %i" % event[0].event_code)

CMPFUNC = CFUNCTYPE(c_void_p, c_void_p, POINTER(drasyl_event_t))
cmp_func = CMPFUNC(on_drasyl_event)

if libdrasyl.drasyl_node_init(thread, cmp_func) != 0:
    print("could not init node")


x = POINTER(drasyl_identity_t)()
import code; code.interact(local=dict(globals(), **locals()))


if libdrasyl.drasyl_node_start(thread) != 0:
    print("could not start node")

print("Wait for node to become online...")
while libdrasyl.drasyl_node_is_online(thread) != 0:
    libdrasyl.drasyl_util_delay(thread, 50)

recipient = "78483253e5dbbe8f401dd1bd1ef0b6f1830c46e411f611dc93a664c1e44cc054".encode("UTF-8")
payload = "hello there".encode("UTF-8")
if libdrasyl.drasyl_node_send(thread, recipient, payload, len(payload)) != 0:
    print("could not send message")


libdrasyl.drasyl_util_delay(thread, 10000)

if libdrasyl.drasyl_node_stop(thread) != 0:
    print("could not stop node")

if libdrasyl.drasyl_shutdown_event_loop(thread) != 0:
    print("could not shutdown event loop")

libdrasyl.graal_tear_down_isolate(thread)

#