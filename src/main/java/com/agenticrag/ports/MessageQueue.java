package com.agenticrag.ports;

import com.agenticrag.model.UploadCompleteMessage;

public interface MessageQueue {
    void publish(UploadCompleteMessage message);

    UploadCompleteMessage poll();
}
