package com.agenticrag.infra.inmemory;

import com.agenticrag.model.UploadCompleteMessage;
import com.agenticrag.ports.MessageQueue;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class InMemoryMessageQueue implements MessageQueue {
    private final Queue<UploadCompleteMessage> queue = new ConcurrentLinkedQueue<>();

    @Override
    public void publish(UploadCompleteMessage message) {
        queue.offer(message);
    }

    @Override
    public UploadCompleteMessage poll() {
        return queue.poll();
    }
}
