package io.github.quickmsg.edge.mqtt;

import com.hazelcast.internal.util.UuidUtil;
import io.github.quickmsg.edge.mqtt.endpoint.MqttEndpoint;
import io.github.quickmsg.edge.mqtt.config.MqttConfig;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import reactor.core.publisher.Flux;
import reactor.netty.DisposableServer;
import reactor.netty.resources.LoopResources;
import reactor.netty.tcp.SslProvider;
import reactor.netty.tcp.TcpServer;

import java.io.File;

/**
 * @author luxurong
 */

public class MqttAcceptor implements EndpointAcceptor {

    private final String id;

    private DisposableServer disposableServer;

    public MqttAcceptor() {
        this.id = UuidUtil.newSecureUuidString();
    }

    @Override
    public String id() {
        return this.id;
    }

    @Override
    public Flux<Endpoint<Packet>> accept() {
        return Flux.deferContextual(contextView -> {
            final MqttConfig.MqttItem config = contextView.get(MqttConfig.MqttItem.class);
            final MqttContext mqttContext = contextView.get(MqttContext.class);
            TcpServer tcpServer = config.sslConfig() != null ? ssl(config.sslConfig()) : TcpServer.create();
            return Flux.create(contextFluxSink -> {
                tcpServer.port(config.port())
                        .wiretap(false)
                        .childOption(ChannelOption.SO_KEEPALIVE, true)
                        .childOption(ChannelOption.TCP_NODELAY, true)
                        .option(ChannelOption.SO_REUSEADDR, true)
                        .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                        .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                        .metrics(false)
                        .runOn(LoopResources.create("NodeMQ", Runtime.getRuntime().availableProcessors(), true))
                        .doOnConnection(connection -> {
                            connection
                                    .addHandlerLast(MqttEncoder.INSTANCE)
                                    .addHandlerLast(new MqttDecoder(config.maxMessageSize()));
                            contextFluxSink.next(new MqttEndpoint(config.connectTimeout(),connection,
                                    connection.channel().remoteAddress().toString()));

                        })
                        .bind()
                        .doOnSuccess(disposableServer -> {
                            this.disposableServer = disposableServer;
                            mqttContext.getLogger().printInfo(
                                    String.format("mqtt start success host：%s port: %d", config.host(), config.port())
                            );
                        })
                        .doOnError(throwable -> {
                            mqttContext.getLogger().printError(
                                    String.format("mqtt start error host：%s port: %d", config.host(), config.port()),throwable
                            );
                        })
                        .subscribe();
            });
        });

    }

    @Override
    public void close() {
        if(disposableServer!=null && !disposableServer.isDisposed()){
            disposableServer.dispose();
        }
    }

    public TcpServer ssl(MqttConfig.SslConfig sslConfig) {
        TcpServer server = TcpServer.create();
        server = server.secure(sslContextSpec -> this.secure(sslContextSpec, sslConfig));
        return server;
    }

    private void secure(SslProvider.SslContextSpec sslContextSpec, MqttConfig.SslConfig sslConfig) {
        try {
            SslContextBuilder sslContextBuilder;
            if (sslConfig != null) {
                sslContextBuilder = SslContextBuilder.forServer(new File(sslConfig.crt()), new File(sslConfig.key()));
                if (sslConfig.ca() != null) {
                    sslContextBuilder = sslContextBuilder.trustManager(new File(sslConfig.ca()));
                    sslContextBuilder.clientAuth(io.netty.handler.ssl.ClientAuth.REQUIRE);
                }
            } else {
                SelfSignedCertificate ssc = new SelfSignedCertificate();
                sslContextBuilder = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey());
            }
            sslContextSpec.sslContext(sslContextBuilder.build());
        } catch (Exception e) {
//            log.info("ssl error",e);
        }

    }
}
