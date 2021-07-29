import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Main {

  private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) throws Exception {

    EventLoopGroup clientGroup = new NioEventLoopGroup();
    SslContext sslContext = SslContextBuilder.forClient()
      .trustManager(InsecureTrustManagerFactory.INSTANCE)
      .sslProvider(SslProvider.OPENSSL)
      .build();

    String hostname = "access.stage.api.bbc.com";
    InetAddress address = InetAddress.getByName(hostname);

    LOGGER.info("Connecting to {}", address.getHostAddress());

    final CountDownLatch latch = new CountDownLatch(1);

    try {
      Bootstrap cb = new Bootstrap();
      cb.group(clientGroup)
        .channel(NioSocketChannel.class)
        .handler(new ChannelInitializer<NioSocketChannel>() {
          @Override
          public void initChannel(NioSocketChannel ch) {
            ch.pipeline().addLast(
              new LoggingHandler(),
              new HttpClientCodec(),
              new HttpContentDecompressor(),
              new HttpObjectAggregator(Integer.MAX_VALUE),
              new ChannelInboundHandlerAdapter() {

                private boolean responseReceived;

                @Override
                public void channelInactive(ChannelHandlerContext ctx) {
                  if (!responseReceived && latch.getCount() > 0) {
                    LOGGER.error("Premature close: connection was closed before receiving the response!!!");
                    latch.countDown();
                  }
                }

                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                  responseReceived = true;
                  LOGGER.info("Received response {}", msg);
                  latch.countDown();
                }
              }
            );
          }
        });

      ChannelFuture whenChannel = cb.connect(new InetSocketAddress(address, 443));

      whenChannel.addListener(f -> {
          if (f.isSuccess()) {
            Channel channel = whenChannel.channel();
            SslHandler sslHandler = new SslHandler(sslContext.newEngine(channel.alloc(), hostname, 443));
            channel.pipeline().addFirst(sslHandler);

            sslHandler.handshakeFuture().addListener(whenHandshaked -> {
              whenHandshaked.addListener(f2 -> {
                  if (f2.isSuccess()) {
                    DefaultHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/v1/user/tokenInfo");
                    request.headers()
                      .set(HttpHeaderNames.HOST, hostname)
                      .set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP_DEFLATE);

                    channel.writeAndFlush(request);
                  } else {
                    LOGGER.error("Handshake failed", f.cause());
                    latch.countDown();
                  }
                }
              );
            });
          } else {
            LOGGER.error("Connect failed", f.cause());
            latch.countDown();
          }
        });

      latch.await(10, TimeUnit.SECONDS);

    } finally {
      clientGroup.shutdownGracefully();
    }
  }
}
