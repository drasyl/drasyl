-- nodes allowed to join network
members = {
    'fe8793d5e8f8a2dcdeada2065b9162596f947619723fe101b8db186b49d01b9b', -- drasyl-3.identity -- h3
    'de7ffbda07bdeb235fa94f208fdc8c01e3fda67aeedb2cc85958153018e6b5e0', -- drasyl-4.identity -- h4
    '9269fff2b347ab343b6ca09dae8ec49f11b6d26c3116c3dfec907d252dd1ea6d', -- drasyl-5.identity -- h5
    'fdc1e564c2fada98bc62c32071cd5cf022c9190893409dc4e1ed1a5c6f4cc3f3', -- drasyl-6.identity -- h6
    'f727a1c83ced687a936f2f7af80bd0b9dda3fabc18fb1dab014e12b3f1deaad9', -- drasyl-7.identity -- h7
    'eaedea1e0255a92343c86008bbf8bf9108982421042d48549a201056747df54a', -- drasyl-8.identity -- h8
}

net = Network()

-- nodes
net.add_node(members[1], { tun_enabled = true, tun_name = 'utun3', tun_subnet = '10.10.2.30/31', tun_address = '10.10.2.30' })
net.add_node(members[2], { tun_enabled = true, tun_name = 'utun4', tun_subnet = '10.10.2.40/31', tun_address = '10.10.2.31' })
net.add_node(members[3], { tun_enabled = true, tun_name = 'utun5', tun_subnet = '10.10.2.50/31', tun_address = '10.10.2.50' })
net.add_node(members[4], { tun_enabled = true, tun_name = 'utun6', tun_subnet = '10.10.2.60/31', tun_address = '10.10.2.60' })
net.add_node(members[5], { tun_enabled = true, tun_name = 'utun7', tun_subnet = '10.10.2.70/31', tun_address = '10.10.2.70' })
net.add_node(members[6], { tun_enabled = true, tun_name = 'utun8', tun_subnet = '10.10.2.80/31', tun_address = '10.10.2.80' })

-- links
for i=1, #members do
    for j=1, #members do
        if i < j then
            net.add_link(members[i], members[j])
        end
    end
end

register_network(net)
