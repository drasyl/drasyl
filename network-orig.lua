-- addresses
center = "9622a56d13cdc7106eaf2045e6b0aa4c086008570e229bf8a0457fdefc838f1d" -- drasyl-3.identity -- h3
leaf1  = "5dd089c433bb7ac1c1d33703796fe302cabfdf057be8976c6e080be3409abe04" -- drasyl-4.identity -- h4
leaf2  = "d8cb6955a56134abda763756134d5b1b619b120833c8480167ea53e98d4ed6af" -- drasyl-5.identity -- h5
leaf3  = "d878b4120399073eddb2c6d309a518a2f30bcaff451da09e43f8cffb6ab4e1bc" -- drasyl-6.identity -- h6
leaf4  = "1208078498273c7604b19246f7aff2435fe5433397abd7e6fee6256c83733f69" -- drasyl-7.identity -- h7
leaf5  = "eaedea1e0255a92343c86008bbf8bf9108982421042d48549a201056747df54a" -- drasyl-8.identity -- h8

-- describes a node
node = params({
  -- if set to true, a TUN device will be created
  tun_enabled = true,
  -- name of the TUN device
  tun_name    = "utun0",
  -- IP subnet the TUN device will route traffic for
  tun_subnet  = "10.10.2.0/24",
  -- MTU size
  tun_mtu     = 1225,
})

-- describes a leaf node
leaf_node = node:extend({})

-- describes a center node
center_node = node:org.drasyl.cli.sdo.config.UtilLib({})

-- describes a channel
channel = {
  -- if set to true, a direct link is established proactively
  -- if set fo false, direct link establishment is prevented (traffic is relayed through rendezvous peer)
  -- if set to null, direct link is established on demand
  directPath = null,

  -- if set to true, a IP route for the TUN device is created
  tun_route = true,
}

net.addNode(center, center_node:org.drasyl.cli.sdo.config.UtilLib({ tun_address = '10.10.2.3' }))
net.addNode(leaf1,    leaf_node:org.drasyl.cli.sdo.config.UtilLib({ tun_address = '10.10.2.4' }))
net.addNode(leaf2,    leaf_node:org.drasyl.cli.sdo.config.UtilLib({ tun_address = '10.10.2.5' }))
net.addNode(leaf3,    leaf_node:org.drasyl.cli.sdo.config.UtilLib({ tun_address = '10.10.2.6' }))
net.addNode(leaf4,    leaf_node:org.drasyl.cli.sdo.config.UtilLib({ tun_address = '10.10.2.7' }))
net.addNode(leaf5,    leaf_node:org.drasyl.cli.sdo.config.UtilLib({ tun_address = '10.10.2.8' }))

net.addLink(center, leaf1, channel)
net.addLink(center, leaf2, channel)
net.addLink(center, leaf3, channel)
net.addLink(center, leaf4, channel)
net.addLink(center, leaf5, channel)
