package com.github.cloudgyb.http.proxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 代理服务器实现
 *
 * @author cloudgyb
 */
public class ProxyServer {
    private final static Logger logger = LoggerFactory.getLogger(App.class);
    private final NioEventLoopGroup boss = new NioEventLoopGroup();
    private final NioEventLoopGroup worker = new NioEventLoopGroup();
    private final String host;
    private final int port;

    public ProxyServer(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        try {
            ChannelFuture future = serverBootstrap
                    .channel(NioServerSocketChannel.class)
                    .group(boss, worker)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childHandler(new ChannelInitializer<NioSocketChannel>() {
                        @Override
                        protected void initChannel(NioSocketChannel channel) {
                            channel.pipeline()
                                    .addLast(new HttpServerCodec())
                                    .addLast(new HttpObjectAggregator(1024))
                                    .addLast(new ProxyServerHandler());
                        }
                    }).bind(host, port);
            future.addListener(future1 -> {
                if (future1.isSuccess()) {
                    logger.info("Proxy Server listen at {}:{}...", host, port);
                }
            });
            future.sync().channel().closeFuture().sync();
        } catch (InterruptedException e) {
            logger.error("产生中断异常！");
        } finally {
            worker.shutdownGracefully();
            boss.shutdownGracefully();
        }
    }
}
