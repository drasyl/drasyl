-- nodes allowed to join network
members = {
    '9622a56d13cdc7106eaf2045e6b0aa4c086008570e229bf8a0457fdefc838f1d', -- drasyl-3.identity -- h3
    '5dd089c433bb7ac1c1d33703796fe302cabfdf057be8976c6e080be3409abe04', -- drasyl-4.identity -- h4
    'd8cb6955a56134abda763756134d5b1b619b120833c8480167ea53e98d4ed6af', -- drasyl-5.identity -- h5
    'd878b4120399073eddb2c6d309a518a2f30bcaff451da09e43f8cffb6ab4e1bc', -- drasyl-6.identity -- h6
    '1208078498273c7604b19246f7aff2435fe5433397abd7e6fee6256c83733f69', -- drasyl-7.identity -- h7
    'eaedea1e0255a92343c86008bbf8bf9108982421042d48549a201056747df54a', -- drasyl-8.identity -- h8
}

net = Network()

-- nodes
net.add_node(members[1])
net.add_node(members[2])
net.add_node(members[3])
net.add_node(members[4])
net.add_node(members[5])
net.add_node(members[6])

-- links
for i=1, #members do
    for j=1, #members do
        if i < j then
            net.add_link(members[i], members[j])
        end
    end
end

register_network(net)
