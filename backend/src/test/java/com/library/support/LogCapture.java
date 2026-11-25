package com.library.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

/** Captures Logback events of one logger (by class or name) for the duration of a test; always {@link #close()}. */
public final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Level previousLevel;

    private LogCapture(Logger logger, boolean forceTrace) {
        this.logger = logger;
        this.previousLevel = logger.getLevel();
        if (forceTrace) {
            logger.setLevel(Level.TRACE);
        }
        appender.start();
        logger.addAppender(appender);
    }

    /** Captures one class's logger and lowers its level to TRACE so that every level can be asserted. */
    public static LogCapture of(Class<?> type) {
        return new LogCapture((Logger) LoggerFactory.getLogger(type), true);
    }

    /**
     * Captures everything that reaches the root logger at the level the application really runs with (INFO), without
     * changing any level: this is what an operator would see in the log file.
     */
    public static LogCapture root() {
        return new LogCapture((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME), false);
    }

    public List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    public List<ILoggingEvent> events(Level level) {
        return events().stream().filter(e -> e.getLevel() == level).toList();
    }

    /** Fully formatted messages (arguments substituted) of all captured events. */
    public List<String> messages() {
        return events().stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
        appender.stop();
    }
}
