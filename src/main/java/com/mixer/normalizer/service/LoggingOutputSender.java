package com.mixer.normalizer.service;

import com.mixer.normalizer.dto.OutputEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(HttpOutputSender.class)
public class LoggingOutputSender implements OutputSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingOutputSender.class);

    @Override
    public void send(OutputEvent event) {
        log.info("OUTPUT: {}", event);
    }
}