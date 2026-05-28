package com.mixer.normalizer.service;

import com.mixer.normalizer.dto.OutputEvent;

public interface OutputSender {
    void send(OutputEvent event);
}