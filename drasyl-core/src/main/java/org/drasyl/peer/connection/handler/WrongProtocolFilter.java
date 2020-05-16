/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.peer.connection.handler;

import com.google.common.collect.ImmutableList;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This handler filters incoming messages, that starts with a string of a wrong protocol. Messages
 * that meets this criteria will be dropped.
 * <p>
 * This handler allows quick filtering for the most HTTP/FTP request, that are not supported.
 */
@Sharable
public class WrongProtocolFilter extends SimpleChannelInboundHandler<String> {
    public static final WrongProtocolFilter INSTANCE = new WrongProtocolFilter();
    private static final List<String> HTTP_REQUEST = ImmutableList.of("GET", "HEAD", "POST", "PUT", "DELETE", "CONNECT",
            "OPTIONS", "TRACE");
    private static final List<String> HTTP_RESPONSE = ImmutableList.of("HTTP");
    private static final List<String> FTP_COMMANDS = ImmutableList.of("ABOR", "CWD", "DELE", "LIST", "MDTM", "MKD",
            "NLST", "PASS", "PASV", "PORT", "PWD", "QUIT", "RETR", "RMD", "RNFR", "RNTO", "SITE", "SIZE", "STOR",
            "TYPE", "USER", "ACCT*", "APPE", "CDUP", "HELP", "MODE", "NOOP", "REIN*", "STAT", "STOU", "STRU", "SYST");
    static final List<String> FILTER = ImmutableList.copyOf(Stream.of(HTTP_REQUEST, HTTP_RESPONSE, FTP_COMMANDS)
            .flatMap(Collection::stream).collect(Collectors.toList()));

    private WrongProtocolFilter() {
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
        if (FILTER.stream().anyMatch(msg::startsWith)) {
            ReferenceCountUtil.release(msg);
            throw new IllegalArgumentException("Your request uses a wrong protocol.");
        }

        ctx.fireChannelRead(msg);
    }
}
