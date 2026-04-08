package org.wang.fileshare.p2p;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * P2P File Transfer Server using Netty
 * Handles direct file transfers between devices
 */
@Slf4j
@Component
public class P2PServer {

    @Value("${p2p.server.port:8082}")
    private int p2pPort;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    @PostConstruct
    public void start() {
        new Thread(this::run).start();
    }

    public void run() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            // HTTP codec
                            pipeline.addLast(new HttpServerCodec());
                            // Aggregator for handling large files
                            pipeline.addLast(new HttpObjectAggregator(65536));
                            // Chunked writer for large files
                            pipeline.addLast(new ChunkedWriteHandler());
                            // Custom handler for file transfer
                            pipeline.addLast(new FileTransferHandler());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture future = bootstrap.bind(p2pPort).sync();
            serverChannel = future.channel();
            
            log.info("P2P File Transfer Server started on port {}", p2pPort);
            System.out.println("P2P Server listening on port " + p2pPort);
            
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("P2P Server interrupted", e);
        } finally {
            shutdown();
        }
    }

    @PreDestroy
    public void shutdown() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.info("P2P Server shutdown complete");
    }

    public int getP2pPort() {
        return p2pPort;
    }
}
