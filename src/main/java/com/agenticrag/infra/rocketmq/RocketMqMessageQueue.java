package com.agenticrag.infra.rocketmq;

import com.agenticrag.model.UploadCompleteMessage;
import com.agenticrag.ports.MessageQueue;
import org.apache.rocketmq.spring.core.RocketMQTemplate;

public class RocketMqMessageQueue implements MessageQueue {
    private final RocketMQTemplate rocketMQTemplate;
    private final String topic;

    public RocketMqMessageQueue(RocketMQTemplate rocketMQTemplate, String topic) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.topic = topic;
    }

    @Override
    public void publish(UploadCompleteMessage message) {
        rocketMQTemplate.convertAndSend(topic, message);
    }

    @Override
    public UploadCompleteMessage poll() {
        return null;
    }
}
