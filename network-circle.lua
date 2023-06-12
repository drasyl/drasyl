-- nodes allowed to join network
members = {
    '9622a56d13cdc7106eaf2045e6b0aa4c086008570e229bf8a0457fdefc838f1d', -- drasyl-3.identity -- h3
    '5dd089c433bb7ac1c1d33703796fe302cabfdf057be8976c6e080be3409abe04', -- drasyl-4.identity -- h4
    'd8cb6955a56134abda763756134d5b1b619b120833c8480167ea53e98d4ed6af', -- drasyl-5.identity -- h5
    'd878b4120399073eddb2c6d309a518a2f30bcaff451da09e43f8cffb6ab4e1bc', -- drasyl-6.identity -- h6
    '1208078498273c7604b19246f7aff2435fe5433397abd7e6fee6256c83733f69', -- drasyl-7.identity -- h7
    'eaedea1e0255a92343c86008bbf8bf9108982421042d48549a201056747df54a', -- drasyl-8.identity -- h8
}

network_listener = function(net)
    -- get all online nodes
    online_nodes = {}
    nodes = net.nodes()
    for i = 1, #nodes do
        node = nodes[i]
        if node.state.online then
            table.insert(online_nodes, node)
        end
    end

    net.clear_links()
    if #online_nodes > 1 then
        for i = 1, #online_nodes do
            current  = online_nodes[i]
            previous = online_nodes[(i - 2) % #online_nodes + 1]
            next     = online_nodes[i % #online_nodes + 1]

            print("#online_nodes " .. #online_nodes)

            net.add_link(current.name, previous.name)
            net.add_link(current.name, next.name)
            current.default_route = next.name
        end
    end
end

net = Network({
    network_listener = network_listener
})

-- nodes
net.add_node(members[1], { tun_address = '10.10.2.3' })
net.add_node(members[2], { tun_address = '10.10.2.4' })
net.add_node(members[3], { tun_address = '10.10.2.5' })
net.add_node(members[4], { tun_address = '10.10.2.6' })
net.add_node(members[5], { tun_address = '10.10.2.7' })
net.add_node(members[6], { tun_address = '10.10.2.8' })

register_network(net)
