package com.github.cloudgyb.http.proxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.AttributeKey;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * 处理 http connect 方法隧道建立
 *
 * @author cloudgyb
 */
public class HttpConnectProxyHandler extends ChannelInboundHandlerAdapter {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final static AttributeKey<Channel> proxyTargetServerChannelAttrKey =
            AttributeKey.newInstance("proxyTargetServerChannel");
    private final static AttributeKey<Channel> proxyServerChannelAttrKey =
            AttributeKey.newInstance("proxyServerChannel");
    private final static NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
    private final Bootstrap bootstrap = new Bootstrap();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof FullHttpRequest req && req.method().equals(HttpMethod.CONNECT)) {
            HttpVersion httpVersion = req.protocolVersion();
            String uri = req.uri();
            if (StringUtils.isBlank(uri)) {
                ctx.close();
            }
            String[] split = uri.split(":");
            String host = split[0];
            int port = Integer.parseInt(split[1]);
            Channel proxyServerChannel = ctx.channel();
            Channel targetServerChannel = connectToTargetServer(host, port);
            proxyServerChannel.attr(proxyTargetServerChannelAttrKey).set(targetServerChannel);
            targetServerChannel.attr(proxyServerChannelAttrKey).set(proxyServerChannel);
            DefaultFullHttpResponse resp = new DefaultFullHttpResponse(httpVersion, HttpResponseStatus.OK);
            resp.setStatus(new HttpResponseStatus(200, "Connection established"));
            ctx.writeAndFlush(resp);
            ctx.pipeline().remove(HttpServerCodec.class);
            ctx.pipeline().remove(HttpObjectAggregator.class);
        } else {
            Channel targetServerChannel = ctx.channel().attr(proxyTargetServerChannelAttrKey).get();
            targetServerChannel.writeAndFlush(msg);
        }
    }

    /**
     * 与目标服务器建立 TCP 连接，初始化 channel，并绑定代理服务 channel
     *
     * @param host 主机名，域名或者 ip
     * @param port 端口号
     * @return 已成功建立的目标服务器 channel
     */
    private Channel connectToTargetServer(String host, int port) {
        ChannelFuture connect = bootstrap
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) {
                        ch.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuffer>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, ByteBuffer msg) {
                                Channel proxyServerChannel = ctx.channel().attr(proxyServerChannelAttrKey).get();
                                proxyServerChannel.writeAndFlush(msg); // 直接写回数据
                            }
                        });
                    }
                }).connect(host, port);
        connect.addListener(future -> {
            if (future.isSuccess()) {
                logger.info("{}：{} tcp 连接已建立", host, port);
            }
        });
        Channel channel;
        try {
            channel = connect.sync().channel();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return channel;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        //关闭 代理 服务器的连接
        Channel targetServerChannel = ctx.channel().attr(proxyTargetServerChannelAttrKey).get();
        targetServerChannel.close();
    }
}
