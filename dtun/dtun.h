#ifndef WINDRASYLTUN_DTUN_H
#define WINDRASYLTUN_DTUN_H

#include <winsock2.h>
#include <ws2ipdef.h>
#include <iphlpapi.h>
#include <wspiapi.h>

/**
 * Does add an IPv4 address and netmask to the adapter.
 *
 * @param Luid luid pointer
 * @param ip the IPv4 address of this adapter
 * @param mask the network mask
 * @return on error the error code
 */
extern DWORD setIPv4AndNetmask(PNET_LUID Luid, const char *ip, int mask);

/**
* Does add an IPv6 address and netmask to the adapter.
*
* @param Luid luid pointer
* @param ip the IPv6 address of this adapter
* @param mask the network mask
* @return on error the error code
*/
extern DWORD setIPv6AndNetmask(PNET_LUID Luid, const char *ip, int mask);

#endif //WINDRASYLTUN_DTUN_H
