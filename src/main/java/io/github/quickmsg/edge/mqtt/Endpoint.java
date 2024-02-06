package io.github.quickmsg.edge.mqtt;

import io.netty.handler.codec.mqtt.MqttProperties;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author luxurong
 */
public interface Endpoint<M> {

    Mono<Void> write(M message);


    Flux<M> receive();


    boolean isMqtt5();

    boolean connected();


    MqttProperties connectProperties();

    MqttProperties willProperties();

    long connectTime();


}