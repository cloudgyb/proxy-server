package com.github.cloudgyb.http.proxy;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.ssl.*;
import io.netty.handler.stream.ChunkedWriteHandler;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;

@ChannelHandler.Sharable
public class HttpProxyChannelInitializer extends ChannelInitializer<NioSocketChannel> {
    private final SslContext sslContext;

    public HttpProxyChannelInitializer(SSLCertConfig sslCertConfig) {
        try {
            SslContextBuilder sslContextBuilder = SslContextBuilder
                    .forServer(sslCertConfig.getCert(), sslCertConfig.getPrivateKey())
                    .clientAuth(ClientAuth.NONE);
            ApplicationProtocolConfig apn = new ApplicationProtocolConfig(
                    ApplicationProtocolConfig.Protocol.ALPN,
                    ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                    ApplicationProtocolConfig.SelectedListenerFailureBehavior.CHOOSE_MY_LAST_PROTOCOL,
                    ApplicationProtocolNames.HTTP_1_1,
                    ApplicationProtocolNames.HTTP_2
            );
            sslContextBuilder.applicationProtocolConfig(apn);
            sslContext = sslContextBuilder.build();
        } catch (SSLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void initChannel(NioSocketChannel channel) {
        SSLEngine sslEngine = sslContext.newEngine(channel.alloc());
        sslEngine.setUseClientMode(false);
        SslHandler sslHandler = new SslHandler(sslEngine, false);
        channel.pipeline()
                .addLast("http", new HttpServerCodec())
                .addLast("httpObjAgg", new HttpObjectAggregator(2048))
                .addLast("proxyConnect", new HttpConnectProxyHandler());
                /*.addLast("ssl", sslHandler)
                .addLast(getServerAPNHandler());*/
    }

    public static ApplicationProtocolNegotiationHandler getServerAPNHandler() {
        return new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_2) {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                    ctx.pipeline().addLast(
                            Http2FrameCodecBuilder.forServer().build(),
                            new ChunkedWriteHandler()
                    );
                } else if (ApplicationProtocolNames.HTTP_1_1.equals(protocol)) {
                    ctx.pipeline()
                            .addLast("httpRequestDecoder", new HttpServerCodec())
                            .addLast("httpAgg", new HttpObjectAggregator(512))
                            .addLast("chunked", new ChunkedWriteHandler());
                } else {
                    throw new IllegalStateException("Protocol: " + protocol + " not supported");
                }
            }
        };
    }
}
