#
# Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
# EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
# MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
# IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
# DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
# OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
# OR OTHER DEALINGS IN THE SOFTWARE.
#
from ctypes import *
import time
import os
import sys
import platform

_dir = os.path.dirname(os.path.realpath(__file__))
# get the right filename
if platform.uname()[0] == "Windows":
    _name = "libdrasyl.dll"
elif platform.uname()[0] == "Linux":
    _name = "libdrasyl.so"
else:
    _name = "libdrasyl.dylib"
_libdrasyl = cdll.LoadLibrary(os.path.join(_dir, "libdrasyl", _name))

#
# structs
#
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

#
# utilities
#

class _dotdict(dict):
    """dot.notation access to dictionary attributes"""
    __getattr__ = dict.get
    __setattr__ = dict.__setitem__
    __delattr__ = dict.__delitem__

_thread = None

def _ensure_thread_created():
    global _thread
    if _thread == None:
        _thread = c_void_p()
        if _libdrasyl.graal_create_isolate(None, None, byref(_thread)) != 0:
            _thread = None
            raise BaseException("initialization error")

def _wrap_event(event):
    response = {
        'event_code': event[0].event_code
    }

    if 10 <= event[0].event_code and event[0].event_code <= 15:
        response['node'] = _dotdict({
            'identity': _dotdict({
                'proof_of_work': event[0].node[0].identity[0].proof_of_work,
                'identity_public_key': event[0].node[0].identity[0].identity_public_key.decode('UTF-8'),
                'identity_secret_key': event[0].node[0].identity[0].identity_secret_key.decode('UTF-8')
            })
        })
    elif 20 <= event[0].event_code and event[0].event_code <= 23:
        response['peer'] = _dotdict({
            'address': event[0].peer[0].address.decode('UTF-8')
        })
    elif event[0].event_code == 30:
        response['sender'] = event[0].message_sender.decode('UTF-8')
        response['payload'] = event[0].message_payload.decode('UTF-8')

    return _dotdict(response)

#
# drasyl API
#

DRASYL_LOG_TRACE = 300
DRASYL_LOG_DEBUG = 500
DRASYL_LOG_INFO = 800
DRASYL_LOG_WARN = 900
DRASYL_LOG_ERROR = 1000

# Signals that the node has been started
DRASYL_EVENT_NODE_UP = 10
# Signals that the node is shut down
DRASYL_EVENT_NODE_DOWN = 11
# Signals that the node is currently connected to a super peer
DRASYL_EVENT_NODE_ONLINE = 12
# Signals that the node is currently not connected to a super peer
DRASYL_EVENT_NODE_OFFLINE = 13
# Signals that the node encountered an unrecoverable error
DRASYL_EVENT_NODE_UNRECOVERABLE_ERROR = 14
# Signals that the node has terminated normally
DRASYL_EVENT_NODE_NORMAL_TERMINATION = 15
# Signals that the node has established a direct connection to a peer
DRASYL_EVENT_PEER_DIRECT = 20
# Signals that communication with this peer is only possible by relaying messages via a super peer
DRASYL_EVENT_PEER_RELAY = 21
# Signals that currently all messages from and to the peer are encrypted with a long time key
DRASYL_EVENT_LONG_TIME_ENCRYPTION = 22
# Signals that currently all messages from and to the {@code #peer} are encrypted with an ephemeral session key
DRASYL_EVENT_PERFECT_FORWARD_SECRECY_ENCRYPTION = 23
# Signals that the node has received a message addressed to it
DRASYL_EVENT_MESSAGE = 30
# Signals that the node was unable to process an inbound message
DRASYL_EVENT_INBOUND_EXCEPTION = 40

def drasyl_node_version():
    global _thread
    _ensure_thread_created()

    version = _libdrasyl.drasyl_node_version(_thread)
    return "{}.{}.{}".format((version >> 24) & 0xff, (version >> 16) & 0xff, (version >> 8) & 0xff)

_on_log_message_func = None
def drasyl_set_logger(on_log_message):
    global _thread, _on_log_message_func
    _ensure_thread_created()

    ON_LOG_MESSAGE_FUNC = CFUNCTYPE(c_void_p, c_void_p, c_int, c_ulong, c_char_p)
    _on_log_message_func = ON_LOG_MESSAGE_FUNC(lambda thread, level, time, message: on_log_message(level, time, message))
    if _libdrasyl.drasyl_set_logger(_thread, _on_log_message_func) != 0:
        raise BaseException("could not set logger")

_on_event_func = None
def drasyl_node_init(config, on_event):
    global _thread, _on_event_func
    _ensure_thread_created()

    ON_EVENT_FUNC = CFUNCTYPE(c_void_p, c_void_p, POINTER(drasyl_event_t))
    _on_event_func = ON_EVENT_FUNC(lambda thread, event: on_event(_wrap_event(event)))
    config_len = len(config) if config else 0
    if _libdrasyl.drasyl_node_init(_thread, config, config_len, _on_event_func) != 0:
        raise BaseException("could not init node")

def drasyl_node_identity():
    global _thread
    _ensure_thread_created()

    identity = drasyl_identity_t()
    if _libdrasyl.drasyl_node_identity(_thread, byref(identity)) != 0:
        raise BaseException("could not retrieve identity")

    return _dotdict({
        'proof_of_work': identity.proof_of_work,
        'identity_public_key': identity.identity_public_key.decode('UTF-8'),
        'identity_secret_key': identity.identity_secret_key.decode('UTF-8')
    })

def drasyl_node_start():
    global _thread
    _ensure_thread_created()

    if _libdrasyl.drasyl_node_start(_thread) != 0:
        raise BaseException("could not start node")

def drasyl_node_is_online():
    global _thread
    _ensure_thread_created()

    return _libdrasyl.drasyl_node_is_online(_thread) == 1

def drasyl_node_send(recipient, payload):
    global _thread
    _ensure_thread_created()

    if _libdrasyl.drasyl_node_send(_thread, recipient, payload, len(payload)) != 0:
        raise BaseException("could not send message")

def drasyl_node_stop():
    global _thread
    _ensure_thread_created()

    if _libdrasyl.drasyl_node_stop(_thread) != 0:
        raise BaseException("could not stop node")

def drasyl_shutdown_event_loop():
    global _thread
    _ensure_thread_created()

    if _libdrasyl.drasyl_shutdown_event_loop(_thread) != 0:
        raise BaseException("could not shutdown event loop")

def thread_tear_down():
    global _thread
    if _thread != None:
        _libdrasyl.graal_tear_down_isolate(_thread)
        _thread = None
