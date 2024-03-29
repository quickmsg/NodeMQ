package io.github.quickmsg.edge.mqtt;

import io.github.quickmsg.edge.mqtt.auth.MqttAuthenticator;

import io.github.quickmsg.edge.mqtt.config.MqttConfig;
import io.github.quickmsg.edge.mqtt.endpoint.MqttEndpointRegistry;
import io.github.quickmsg.edge.mqtt.log.AsyncLogger;
import io.github.quickmsg.edge.mqtt.packet.*;
import io.github.quickmsg.edge.mqtt.process.MqttProcessor;
import io.github.quickmsg.edge.mqtt.topic.MqttTopicRegistry;
import io.github.quickmsg.edge.mqtt.util.JsonReader;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author luxurong
 */
public class MqttContext implements Context, Consumer<Packet> {

    private final Map<String, MqttAcceptor> mqttContext = new HashMap<>();

    private final EndpointRegistry endpointRegistry;

    private final TopicRegistry topicRegistry;

    private final MqttProcessor mqttProcessor;

    private final Authenticator authenticator;

    private  AsyncLogger asyncLogger;

    private MqttConfig mqttConfig;

    private final Scheduler scheduler;


    public MqttContext() {
        this(new MqttEndpointRegistry(), new MqttTopicRegistry(),new MqttAuthenticator());
    }

    public MqttContext(EndpointRegistry endpointRegistry, TopicRegistry topicRegistry, Authenticator authenticator ) {
        this.scheduler = Schedulers.newParallel("event",Runtime.getRuntime().availableProcessors());
        this.endpointRegistry = endpointRegistry;
        this.topicRegistry = topicRegistry;
        this.mqttProcessor = new MqttProcessor(this);
        this.authenticator = authenticator;
    }


    @Override
    public Flux<Packet> start() {
        this.mqttConfig = readConfig();
        if( this.mqttConfig==null){
            this.mqttConfig = MqttConfig.defaultConfig();
        }
        this.asyncLogger = new AsyncLogger( this.mqttConfig.log());
        return Flux.fromIterable(mqttConfig.mqtt())
                .flatMap(mqttItem -> {
                    final MqttAcceptor mqttAcceptor = new MqttAcceptor();
                    mqttContext.put(mqttAcceptor.id(), mqttAcceptor);
                    return mqttAcceptor.accept()
                            .contextWrite(context -> context.put(MqttConfig.MqttItem.class, mqttItem))
                            .contextWrite(context -> context.put(MqttContext.class, this));
                })
                .flatMap(Endpoint::receive)
                .subscribeOn(Schedulers.newParallel("event", Runtime.getRuntime().availableProcessors()))
                .doOnNext(this)
                .onErrorContinue((throwable, o) -> {
                    this.asyncLogger.printError("mqtt accept error",throwable);
                });
    }

    @Override
    public Map<String, MqttAcceptor> getMqttAcceptors() {
        return this.mqttContext;
    }

    @Override
    public TopicRegistry getTopicRegistry() {
        return this.topicRegistry;
    }

    @Override
    public EndpointRegistry getChannelRegistry() {
        return this.endpointRegistry;
    }

    @Override
    public Authenticator getAuthenticator() {
        return this.authenticator;
    }
    @Override
    public MqttConfig getMqttConfig() {
        return mqttConfig;
    }

    @Override
    public AsyncLogger getLogger() {
        return this.asyncLogger;
    }

    private MqttConfig readConfig() {
        return JsonReader.readJson("mqtt.json",MqttConfig.class);
    }

    @Override
    public void accept(Packet packet) {
        switch (packet) {
            case PublishPacket publishPacket -> mqttProcessor.processPublish(publishPacket)
                    .subscribeOn(scheduler).subscribe();
            case SubscribePacket subscribePacket -> mqttProcessor.processSubscribe(subscribePacket)
                    .subscribeOn(scheduler).subscribe();
            case ConnectPacket connectPacket-> mqttProcessor.processConnect(connectPacket)
                    .subscribeOn(scheduler).subscribe();
            case DisconnectPacket disconnectPacket->mqttProcessor.processDisconnect(disconnectPacket)
                    .subscribeOn(scheduler).subscribe();
            case PublishAckPacket publishAckPacket->mqttProcessor.processPublishAck(publishAckPacket)
                    .subscribeOn(scheduler).subscribe();
            case AuthPacket authPacket->mqttProcessor.processAuth(authPacket)
                    .subscribeOn(scheduler).subscribe();
            case UnsubscribePacket unsubscribePacket->mqttProcessor.processUnSubscribe(unsubscribePacket)
                    .subscribeOn(scheduler).subscribe();
            case PublishRelPacket publishRelPacket ->mqttProcessor.processPublishRel(publishRelPacket)
                    .subscribeOn(scheduler).subscribe();
            case PublishRecPacket publishRecPacket->mqttProcessor.processPublishRec(publishRecPacket)
                    .subscribeOn(scheduler).subscribe();
            case PublishCompPacket publishCompPacket->mqttProcessor.processPublishComp(publishCompPacket)
                    .subscribeOn(scheduler).subscribe();
            case PingPacket pingPacket->mqttProcessor.processPing(pingPacket)
                    .subscribeOn(scheduler).subscribe();
            case ClosePacket closePacket->mqttProcessor.processClose(closePacket)
                    .subscribeOn(scheduler).subscribe();
            default -> {
            }
        }
    }
}
