package com.github.cloudgyb.http.proxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Hello world!
 */
public class App {
    private final static Logger logger = LoggerFactory.getLogger(App.class);
    private final static String host = "localhost";
    private final static int port = 80;

    public static void main(String[] args) {
        File certFile = new File("C:\\Users\\Administrator\\Desktop\\cert.pem");
        File certKeyFile = new File("C:\\Users\\Administrator\\Desktop\\private.key");
        ProxyServer proxyServer = new ProxyServer(host, port);
        proxyServer.start();
    }

}
