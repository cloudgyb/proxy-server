package com.github.cloudgyb.http.proxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;

/**
 * 代理请求处理器
 *
 * @author cloudgyb
 */
public class ProxyServerHandler extends ChannelInboundHandlerAdapter {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final static AttributeKey<Channel> targetServerAttrKey = AttributeKey.newInstance("targetServerChannel");
    final static AttributeKey<Channel> proxyServerAttrKey = AttributeKey.newInstance("proxyServerChannel");
    private final static NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
    private final Bootstrap bootstrap = new Bootstrap();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof FullHttpRequest req) {
            HttpMethod method = req.method();
            if (method.equals(HttpMethod.CONNECT)) { // https 代理处理
                String uri = req.uri();
                String[] split = uri.split(":");
                String host = split[0];
                String port = split[1];
                Channel targetServerChannel = createTargetServerTcpTunnelChannel(host, Integer.parseInt(port));
                Channel proxyServerChannel = ctx.channel();
                // 目标服务器 channel 与 代理服务器 channel 互相绑定，用于互相转发数据
                proxyServerChannel.attr(targetServerAttrKey).set(targetServerChannel);
                targetServerChannel.attr(proxyServerAttrKey).set(proxyServerChannel);
                // 响应 200 Connection established，表示隧道建立成功
                DefaultFullHttpResponse resp = new DefaultFullHttpResponse(req.protocolVersion(), HttpResponseStatus.OK);
                resp.setStatus(new HttpResponseStatus(200, "Connection established"));
                ctx.writeAndFlush(resp);
            } else { // http 代理处理
                String hostHeader = req.headers().get("host");
                String host;
                String port;
                if (hostHeader.contains(":")) {
                    String[] split = hostHeader.split(":");
                    host = split[0];
                    port = split[1];
                } else {
                    host = hostHeader;
                    port = "80";
                }
                Channel targetServerChannel = createTargetServerTcpChannel(host, Integer.parseInt(port));
                Channel proxyServerChannel = ctx.channel();
                // 目标服务器 channel 与 代理服务器 channel 互相绑定，用于互相转发数据
                proxyServerChannel.attr(targetServerAttrKey).set(targetServerChannel);
                targetServerChannel.attr(proxyServerAttrKey).set(proxyServerChannel);
                targetServerChannel.writeAndFlush(req);
            }
            ctx.pipeline().remove(HttpObjectAggregator.class);
            ctx.pipeline().remove(HttpServerCodec.class);
        } else {
            Channel targetServerChannel = ctx.channel().attr(targetServerAttrKey).get();
            targetServerChannel.writeAndFlush(msg);
        }
    }

    private Channel createTargetServerTcpTunnelChannel(String host, int port) {
        return connectToTargetServer(host, port, new ProxyHttpsTunnelChannelInitializer());
    }

    private Channel createTargetServerTcpChannel(String host, int port) {
        return connectToTargetServer(host, port, new ProxyHttpChannelInitializer());
    }

    /**
     * 连接到目标服务器
     *
     * @param host               主机名或者 ip 地址
     * @param port               端口号
     * @param channelInitializer channel 初始化器
     * @return 连接建立就绪的 channel
     */
    private Channel connectToTargetServer(String host, int port,
                                          ChannelInitializer<? extends NioSocketChannel> channelInitializer) {
        ChannelFuture future = bootstrap
                .channel(NioSocketChannel.class)
                .group(eventLoopGroup)
                .handler(channelInitializer)
                .connect(host, port);

        future.addListener(future1 -> {
            if (future1.isSuccess()) {
                logger.info("目标服务器{}：{}连接已建立！", host, port);
            }
        });

        try {
            return future.sync().channel();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        Channel channel = ctx.channel().attr(targetServerAttrKey).get();
        if (channel != null) {
            SocketAddress socketAddress = channel.remoteAddress();
            logger.info("代理服务器连接关闭，关闭目标服务器{}连接！", socketAddress);
            channel.close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        Channel channel = ctx.channel();
        logger.error("连接{}出现异常{}.关闭连接！", channel.remoteAddress(), cause.getMessage());
        ctx.close();
    }
}