package com.github.cloudgyb.http.proxy;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.nio.NioSocketChannel;

import static com.github.cloudgyb.http.proxy.ProxyServerHandler.proxyServerAttrKey;

public class ProxyHttpsTunnelChannelInitializer extends ChannelInitializer<NioSocketChannel> {
    @Override
    protected void initChannel(NioSocketChannel channel) {
        channel.pipeline()
                .addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        Channel proxyServerChannel = ctx.channel().attr(proxyServerAttrKey).get();
                        proxyServerChannel.writeAndFlush(msg);
                    }
                });
    }
}