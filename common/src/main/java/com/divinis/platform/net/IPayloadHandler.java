package com.divinis.platform.net;

@FunctionalInterface
public interface IPayloadHandler<T> {
    void handle(T payload, IPayloadContext context);
}
