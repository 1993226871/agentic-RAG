package com.agenticrag.infra.rocketmq;

import com.agenticrag.model.UploadCompleteMessage;
import com.agenticrag.service.AsyncIngestionConsumer;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "rag.mock-enabled", havingValue = "false")
@RocketMQMessageListener(
        topic = "${rag.mq.topic}",
        consumerGroup = "${rag.mq.consumer-group}"
)
public class RocketMqUploadCompleteListener implements RocketMQListener<UploadCompleteMessage> {
    private static final Logger log = LoggerFactory.getLogger(RocketMqUploadCompleteListener.class);
    private final AsyncIngestionConsumer ingestionConsumer;

    public RocketMqUploadCompleteListener(AsyncIngestionConsumer ingestionConsumer) {
        this.ingestionConsumer = ingestionConsumer;
    }

    @Override
    public void onMessage(UploadCompleteMessage message) {
        log.info("RocketMQ message received fileId={}, objectKey={}", message.fileId(), message.objectKey());
        ingestionConsumer.consume(message);
        log.info("RocketMQ message consumed successfully for fileId={}", message.fileId());
    }
}
