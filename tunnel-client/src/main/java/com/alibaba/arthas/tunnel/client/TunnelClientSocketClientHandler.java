
package com.alibaba.arthas.tunnel.client;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.QueryStringEncoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

/**
 * 
 * @author hengyunabc 2019-08-28
 *
 */
public class TunnelClientSocketClientHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    private final static Logger logger = LoggerFactory.getLogger(TunnelClientSocketClientHandler.class);

    private TunnelClient tunnelClient;

    public TunnelClientSocketClientHandler(TunnelClient tunnelClient) {
        this.tunnelClient = tunnelClient;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        if (frame instanceof TextWebSocketFrame) {
            TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
            String text = textFrame.text();

            logger.info("receive TextWebSocketFrame: {}", text);

            QueryStringDecoder queryDecoder = new QueryStringDecoder(text);
            Map<String, List<String>> parameters = queryDecoder.parameters();
            List<String> methodList = parameters.get("method");
            String method = null;
            if (methodList != null && !methodList.isEmpty()) {
                method = methodList.get(0);
            }

            if ("agentRegister".equals(method)) {
                List<String> idList = parameters.get("id");
                if (idList != null && !idList.isEmpty()) {
                    this.tunnelClient.setId(idList.get(0));
                }
            }

            if ("startTunnel".equals(method)) {
                QueryStringEncoder queryEncoder = new QueryStringEncoder(this.tunnelClient.getTunnelServerUrl());
                queryEncoder.addParam("method", "openTunnel");
                queryEncoder.addParam("clientConnectionId", parameters.get("clientConnectionId").get(0));
                queryEncoder.addParam("id", parameters.get("id").get(0));

                URI forwardUri = queryEncoder.toUri();

                logger.info("start ForwardClient, uri: {}", forwardUri);
                ForwardClient forwardClient = new ForwardClient(forwardUri,
                        new URI(this.tunnelClient.getLocalServerUrl()), tunnelClient.getEventLoopGroup());
                forwardClient.start();
            }

        }
    }

    @Override
    public void channelUnregistered(final ChannelHandlerContext ctx) throws Exception {
        ctx.channel().eventLoop().schedule(new Runnable() {
            @Override
            public void run() {
                logger.error("try to reconnect to tunnel server, uri: {}", tunnelClient.getTunnelServerUrl());
                try {
                    tunnelClient.connect(true);
                } catch (Throwable e) {
                    logger.error("reconnect error", e);
                }
            }
        }, tunnelClient.getReconnectDelay(), TimeUnit.SECONDS);
    }
}
