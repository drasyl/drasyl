#include "dtun.h"

DWORD setIPv4AndNetmask(NET_LUID *Luid, const char *ip, int mask) {
    MIB_UNICASTIPADDRESS_ROW AddressRow;
    InitializeUnicastIpAddressEntry(&AddressRow);
    memcpy(&AddressRow.InterfaceLuid, Luid, sizeof(NET_LUID));
    inet_pton(AF_INET, ip, &AddressRow.Address.Ipv4.sin_addr);
    AddressRow.Address.Ipv4.sin_family = AF_INET;
    AddressRow.OnLinkPrefixLength = mask;
    AddressRow.DadState = IpDadStatePreferred;

    return CreateUnicastIpAddressEntry(&AddressRow);
}

DWORD setIPv6AndNetmask(NET_LUID *Luid, const char *ip, int mask) {
    MIB_UNICASTIPADDRESS_ROW AddressRow;
    InitializeUnicastIpAddressEntry(&AddressRow);
    memcpy(&AddressRow.InterfaceLuid, Luid, sizeof(NET_LUID));
    inet_pton(AF_INET6, ip, &AddressRow.Address.Ipv6.sin6_addr);
    AddressRow.Address.Ipv6.sin6_family = AF_INET6;
    AddressRow.OnLinkPrefixLength = mask;
    AddressRow.DadState = IpDadStatePreferred;

    return CreateUnicastIpAddressEntry(&AddressRow);
}
