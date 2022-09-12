#ifndef __LIBDRASYL_H
#define __LIBDRASYL_H

#include <graal_isolate.h>


#if defined(__cplusplus)
extern "C" {
#endif

int drasyl_node_version(graal_isolatethread_t*);

int drasyl_set_logger(graal_isolatethread_t*, void *);

int drasyl_node_init(graal_isolatethread_t*, char*, size_t, void *);

int drasyl_node_identity(graal_isolatethread_t*, drasyl_identity_t*);

int drasyl_node_start(graal_isolatethread_t*);

int drasyl_node_stop(graal_isolatethread_t*);

int drasyl_shutdown_event_loop(graal_isolatethread_t*);

int drasyl_node_send(graal_isolatethread_t*, char*, char*, size_t);

int drasyl_node_is_online(graal_isolatethread_t*);

void drasyl_sleep(graal_isolatethread_t*, long long int);

#if defined(__cplusplus)
}
#endif
#endif
