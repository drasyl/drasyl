net = create_network()
net:add_node('n1')
net:add_node('n2')
net:add_link('n1', 'n2')
net:set_callback(function(net, dev)
    print("callback: " .. inspect(net) .. ' ' .. inspect(dev))
    for i, device in ipairs(dev) do
        print('device ' .. inspect(device))
    end
end)

for i, node in ipairs(net:get_nodes()) do
    print('node ' .. inspect(node))
end

for i, link in ipairs(net:get_links()) do
    print('link ' .. inspect(link))
end

register_network(net)
