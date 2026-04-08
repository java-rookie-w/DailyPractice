package org.wang.fileshare.p2p;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.DefaultFileRegion;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * Handler for P2P file transfer operations
 */
@Slf4j
public class FileTransferHandler extends ChannelInboundHandlerAdapter {

    private static final String UPLOAD_DIR = System.getProperty("java.io.tmpdir") + "/file-share/";

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            FullHttpRequest request = (FullHttpRequest) msg;
            String uri = request.uri();
            
            if (uri.startsWith("/upload")) {
                handleUpload(ctx, request);
            } else if (uri.startsWith("/download")) {
                handleDownload(ctx, request);
            } else {
                sendHttpResponse(ctx, request, createResponse("Not Found", HttpResponseStatus.NOT_FOUND));
            }
        }
    }

    private void handleUpload(ChannelHandlerContext ctx, FullHttpRequest request) {
        // Extract filename from headers or URI
        String filename = extractFilename(request);
        File uploadDir = new File(UPLOAD_DIR);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        File uploadedFile = new File(uploadDir, filename);
        
        try (RandomAccessFile raf = new RandomAccessFile(uploadedFile, "rw")) {
            ByteBuf content = request.content();
            byte[] data = new byte[content.readableBytes()];
            content.readBytes(data);
            raf.write(data);
            
            String response = "{\"success\":true,\"filename\":\"" + filename + "\",\"size\":" + data.length + "}";
            sendHttpResponse(ctx, request, createResponse(response, HttpResponseStatus.OK));
            log.info("File uploaded: {} ({} bytes)", filename, data.length);
        } catch (Exception e) {
            String response = "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}";
            sendHttpResponse(ctx, request, createResponse(response, HttpResponseStatus.INTERNAL_SERVER_ERROR));
            log.error("Upload failed", e);
        }
    }

    private void handleDownload(ChannelHandlerContext ctx, FullHttpRequest request) {
        String filename = extractFilename(request);
        File file = new File(UPLOAD_DIR + filename);
        
        if (!file.exists()) {
            sendHttpResponse(ctx, request, createResponse("{\"error\":\"File not found\"}", HttpResponseStatus.NOT_FOUND));
            return;
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long fileLength = file.length();
            
            HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, fileLength);
            response.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
            
            ctx.write(response);
            
            // Send file using zero-copy
            ctx.write(new DefaultFileRegion(raf.getChannel(), 0, fileLength));
            
            // Send end marker
            ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
            lastContentFuture.addListener(ChannelFutureListener.CLOSE);
            
            log.info("File downloaded: {} ({} bytes)", filename, fileLength);
        } catch (Exception e) {
            log.error("Download failed", e);
        }
    }

    private String extractFilename(FullHttpRequest request) {
        String uri = request.uri();
        int lastSlash = uri.lastIndexOf('/');
        return uri.substring(lastSlash + 1);
    }

    private FullHttpResponse createResponse(String content, HttpResponseStatus status) {
        ByteBuf buffer = Unpooled.copiedBuffer(content, StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buffer);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, buffer.readableBytes());
        return response;
    }

    private void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest request, FullHttpResponse response) {
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Exception in file transfer", cause);
        ctx.close();
    }
}
