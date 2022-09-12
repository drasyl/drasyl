#ifndef __LIBDRASYL_H
#define __LIBDRASYL_H

#include <graal_isolate_dynamic.h>


#if defined(__cplusplus)
extern "C" {
#endif

typedef int (*drasyl_node_version_fn_t)(graal_isolatethread_t*);

typedef int (*drasyl_set_logger_fn_t)(graal_isolatethread_t*, void *);

typedef int (*drasyl_node_init_fn_t)(graal_isolatethread_t*, char*, size_t, void *);

typedef int (*drasyl_node_identity_fn_t)(graal_isolatethread_t*, drasyl_identity_t*);

typedef int (*drasyl_node_start_fn_t)(graal_isolatethread_t*);

typedef int (*drasyl_node_stop_fn_t)(graal_isolatethread_t*);

typedef int (*drasyl_shutdown_event_loop_fn_t)(graal_isolatethread_t*);

typedef int (*drasyl_node_send_fn_t)(graal_isolatethread_t*, char*, char*, size_t);

typedef int (*drasyl_node_is_online_fn_t)(graal_isolatethread_t*);

typedef void (*drasyl_sleep_fn_t)(graal_isolatethread_t*, long long int);

#if defined(__cplusplus)
}
#endif
#endif
