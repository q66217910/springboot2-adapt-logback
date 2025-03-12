# Springboot2日志适配logback13

示例代码github: https://github.com/q66217910/springboot2-adapt-logback13

## 背景

Springboot2默认使用logback作为日志框架，目前1.2.x版本的有漏洞风险，需要升级到1.3.x版本。
但是logback1.3.0版本的日志格式与1.2.3版本有所不同，需要进行适配。

## 适配步骤

### 1. 添加logback1.3与slf4j依赖

```xml
<project>
    <properties>
        <logback.version>1.3.15</logback.version>
        <slf4j.version>2.0.12</slf4j.version>
        <springboot.version>2.7.18</springboot.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-actuator-autoconfigure</artifactId>
            <version>${springboot.version}</version>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <version>${logback.version}</version>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>jul-to-slf4j</artifactId>
            <version>${slf4j.version}</version>
            <optional>true</optional>
        </dependency>
    </dependencies>
</project>
```

### 2. 实现自定义工厂LoggingSystemFactory

代码可以从springboot3.x的源码中拷贝过来。(建议拷贝低版本，高版本会使用logback1.5.x)

```java
/*
 * Copyright 2012-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.jul.LevelChangePropagator;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.spi.FilterReply;
import ch.qos.logback.core.status.OnConsoleStatusListener;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.util.StatusListenerConfigHelper;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.slf4j.helpers.SubstituteLoggerFactory;
import org.springframework.boot.logging.LogFile;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.logging.LoggingSystemFactory;
import org.springframework.boot.logging.LoggingSystemProperties;
import org.springframework.boot.logging.Slf4JLoggingSystem;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogManager;

/**
 * {@link LoggingSystem} for <a href="https://logback.qos.ch">logback</a>.
 *
 * @author Phillip Webb
 * @author Dave Syer
 * @author Andy Wilkinson
 * @author Ben Hale
 * @since 1.0.0
 */
public class LogbackLogging13System extends Slf4JLoggingSystem {

    private static final String CONFIGURATION_FILE_PROPERTY = "logback.configurationFile";

    private static final LogLevels<Level> LEVELS = new LogLevels<>();
    private static final TurboFilter FILTER = new TurboFilter() {

        @Override
        public FilterReply decide(
                Marker marker,
                ch.qos.logback.classic.Logger logger,
                Level level,
                String format,
                Object[] params,
                Throwable t) {
            return FilterReply.DENY;
        }
    };

    static {
        LEVELS.map(LogLevel.TRACE, Level.TRACE);
        LEVELS.map(LogLevel.TRACE, Level.ALL);
        LEVELS.map(LogLevel.DEBUG, Level.DEBUG);
        LEVELS.map(LogLevel.INFO, Level.INFO);
        LEVELS.map(LogLevel.WARN, Level.WARN);
        LEVELS.map(LogLevel.ERROR, Level.ERROR);
        LEVELS.map(LogLevel.FATAL, Level.ERROR);
        LEVELS.map(LogLevel.OFF, Level.OFF);
    }

    public LogbackLogging13System(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public LoggingSystemProperties getSystemProperties(ConfigurableEnvironment environment) {
        return new LogbackLoggingSystemProperties(environment);
    }

    @Override
    protected String[] getStandardConfigLocations() {
        return new String[] {"logback-test.groovy", "logback-test.xml", "logback.groovy", "logback.xml"};
    }

    @Override
    public void beforeInitialize() {
        LoggerContext loggerContext = getLoggerContext();
        if (isAlreadyInitialized(loggerContext)) {
            return;
        }
        super.beforeInitialize();
        loggerContext.getTurboFilterList().add(FILTER);
    }

    @Override
    public void initialize(LoggingInitializationContext initializationContext, String configLocation, LogFile logFile) {
        LoggerContext loggerContext = getLoggerContext();
        if (isAlreadyInitialized(loggerContext)) {
            return;
        }
        super.initialize(initializationContext, configLocation, logFile);
        loggerContext.getTurboFilterList().remove(FILTER);
        markAsInitialized(loggerContext);
        if (StringUtils.hasText(System.getProperty(CONFIGURATION_FILE_PROPERTY))) {
            getLogger(LogbackLoggingSystem.class.getName())
                    .warn("Ignoring '" + CONFIGURATION_FILE_PROPERTY
                            + "' system property. Please use 'logging.config' instead.");
        }
    }

    @Override
    protected void loadDefaults(LoggingInitializationContext initializationContext, LogFile logFile) {
        LoggerContext context = getLoggerContext();
        stopAndReset(context);
        boolean debug = Boolean.getBoolean("logback.debug");
        if (debug) {
            StatusListenerConfigHelper.addOnConsoleListenerInstance(context, new OnConsoleStatusListener());
        }
        Environment environment = initializationContext.getEnvironment();
        // Apply system properties directly in case the same JVM runs multiple apps
        new LogbackLoggingSystemProperties(environment, context::putProperty).apply(logFile);
        LogbackConfigurator configurator =
                debug ? new DebugLogbackConfigurator(context) : new LogbackConfigurator(context);
        new DefaultLogbackConfiguration(logFile).apply(configurator);
        context.setPackagingDataEnabled(true);
    }

    @Override
    protected void loadConfiguration(
            LoggingInitializationContext initializationContext, String location,LogFile logFile) {
        super.loadConfiguration(initializationContext, location, logFile);
        LoggerContext loggerContext = getLoggerContext();
        stopAndReset(loggerContext);
        try {
            configureByResourceUrl(initializationContext, loggerContext, ResourceUtils.getURL(location));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not initialize Logback logging from " + location, ex);
        }
        reportConfigurationErrorsIfNecessary(loggerContext);
    }

    private void reportConfigurationErrorsIfNecessary(LoggerContext loggerContext) {
        StringBuilder errors = new StringBuilder();
        List<Throwable> suppressedExceptions = new ArrayList<>();
        for (Status status : loggerContext.getStatusManager().getCopyOfStatusList()) {
            if (status.getLevel() == Status.ERROR) {
                errors.append((errors.length() > 0) ? String.format("%n") : "");
                errors.append(status.toString());
                if (status.getThrowable() != null) {
                    suppressedExceptions.add(status.getThrowable());
                }
            }
        }
        if (errors.length() == 0) {
            return;
        }
        IllegalStateException ex =
                new IllegalStateException(String.format("Logback configuration error detected: %n%s", errors));
        suppressedExceptions.forEach(ex::addSuppressed);
        throw ex;
    }

    private void configureByResourceUrl(
            LoggingInitializationContext initializationContext, LoggerContext loggerContext, URL url)
            throws JoranException {
        if (url.getPath().endsWith(".xml")) {
            JoranConfigurator configurator = new SpringBootJoran13Configurator(initializationContext);
            configurator.setContext(loggerContext);
            configurator.doConfigure(url);
        } else {
            throw new IllegalArgumentException("Unsupported file extension in '" + url + "'. Only .xml is supported");
        }
    }

    private void stopAndReset(LoggerContext loggerContext) {
        loggerContext.stop();
        loggerContext.reset();
        if (isBridgeHandlerInstalled()) {
            addLevelChangePropagator(loggerContext);
        }
    }

    private boolean isBridgeHandlerInstalled() {
        if (!isBridgeHandlerAvailable()) {
            return false;
        }
        java.util.logging.Logger rootLogger = LogManager.getLogManager().getLogger("");
        Handler[] handlers = rootLogger.getHandlers();
        return handlers.length == 1 && handlers[0] instanceof SLF4JBridgeHandler;
    }

    private void addLevelChangePropagator(LoggerContext loggerContext) {
        LevelChangePropagator levelChangePropagator = new LevelChangePropagator();
        levelChangePropagator.setResetJUL(true);
        levelChangePropagator.setContext(loggerContext);
        loggerContext.addListener(levelChangePropagator);
    }

    @Override
    public void cleanUp() {
        LoggerContext context = getLoggerContext();
        markAsUninitialized(context);
        super.cleanUp();
        context.getStatusManager().clear();
        context.getTurboFilterList().remove(FILTER);
    }

    @Override
    protected void reinitialize(LoggingInitializationContext initializationContext) {
        getLoggerContext().reset();
        getLoggerContext().getStatusManager().clear();
        loadConfiguration(initializationContext, getSelfInitializationConfig(), null);
    }

    @Override
    public List<LoggerConfiguration> getLoggerConfigurations() {
        List<LoggerConfiguration> result = new ArrayList<>();
        for (ch.qos.logback.classic.Logger logger : getLoggerContext().getLoggerList()) {
            result.add(getLoggerConfiguration(logger));
        }
        result.sort(CONFIGURATION_COMPARATOR);
        return result;
    }

    @Override
    public LoggerConfiguration getLoggerConfiguration(String loggerName) {
        String name = getLoggerName(loggerName);
        LoggerContext loggerContext = getLoggerContext();
        return getLoggerConfiguration(loggerContext.exists(name));
    }

    private LoggerConfiguration getLoggerConfiguration(ch.qos.logback.classic.Logger logger) {
        if (logger == null) {
            return null;
        }
        LogLevel level = LEVELS.convertNativeToSystem(logger.getLevel());
        LogLevel effectiveLevel = LEVELS.convertNativeToSystem(logger.getEffectiveLevel());
        String name = getLoggerName(logger.getName());
        return new LoggerConfiguration(name, level, effectiveLevel);
    }

    private String getLoggerName(String name) {
        if (!StringUtils.hasLength(name) || Logger.ROOT_LOGGER_NAME.equals(name)) {
            return ROOT_LOGGER_NAME;
        }
        return name;
    }

    @Override
    public Set<LogLevel> getSupportedLogLevels() {
        return LEVELS.getSupported();
    }

    @Override
    public void setLogLevel(String loggerName, LogLevel level) {
        ch.qos.logback.classic.Logger logger = getLogger(loggerName);
        //noinspection ConstantExpression,ConstantValue
        if (logger != null) {
            logger.setLevel(LEVELS.convertSystemToNative(level));
        }
    }

    @Override
    public Runnable getShutdownHandler() {
        return () -> getLoggerContext().stop();
    }

    private ch.qos.logback.classic.Logger getLogger(String name) {
        LoggerContext factory = getLoggerContext();
        return factory.getLogger(getLoggerName(name));
    }

    private LoggerContext getLoggerContext() {
        ILoggerFactory factory = getLoggerFactory();
        Assert.isInstanceOf(
                LoggerContext.class,
                factory,
                () -> String.format(
                        "LoggerFactory is not a Logback LoggerContext but Logback is on "
                                + "the classpath. Either remove Logback or the competing "
                                + "implementation (%s loaded from %s). If you are using "
                                + "WebLogic you will need to add 'org.slf4j' to "
                                + "prefer-application-packages in WEB-INF/weblogic.xml",
                        factory.getClass(), getLocation(factory)));
        return (LoggerContext) factory;
    }

    private ILoggerFactory getLoggerFactory() {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        while (factory instanceof SubstituteLoggerFactory) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for non-substitute logger factory", ex);
            }
            factory = LoggerFactory.getILoggerFactory();
        }
        return factory;
    }

    private Object getLocation(ILoggerFactory factory) {
        try {
            ProtectionDomain protectionDomain = factory.getClass().getProtectionDomain();
            CodeSource codeSource = protectionDomain.getCodeSource();
            if (codeSource != null) {
                return codeSource.getLocation();
            }
        } catch (SecurityException ex) {
            // Unable to determine location
        }
        return "unknown location";
    }

    private boolean isAlreadyInitialized(LoggerContext loggerContext) {
        return loggerContext.getObject(LoggingSystem.class.getName()) != null;
    }

    private void markAsInitialized(LoggerContext loggerContext) {
        loggerContext.putObject(LoggingSystem.class.getName(), new Object());
    }

    private void markAsUninitialized(LoggerContext loggerContext) {
        loggerContext.removeObject(LoggingSystem.class.getName());
    }

    /**
     * {@link LoggingSystemFactory} that returns {@link LogbackLoggingSystem} if possible.
     */
    @Order(Ordered.HIGHEST_PRECEDENCE + 100)
    public static class Factory implements LoggingSystemFactory {

        private static final boolean PRESENT = ClassUtils.isPresent(
                "ch.qos.logback.classic.spi.LogbackServiceProvider", Factory.class.getClassLoader());

        private static final boolean SPRING_BOOT_V3 = ClassUtils.isPresent(
                "org.springframework.boot.logging.logback.SpringPropertyModelHandler", Factory.class.getClassLoader());

        @Override
        public LoggingSystem getLoggingSystem(ClassLoader classLoader) {
            if (PRESENT && !SPRING_BOOT_V3) {
                return new LogbackLogging13System(classLoader);
            }
            return null;
        }
    }
}
```


### 3. 配置META-INF/spring.factories

```properties
org.springframework.boot.logging.LoggingSystemFactory=org.springframework.boot.logging.logback.LogbackLogging13System$Factory
```

### 4. 实现解析器SpringBootJoran13Configurator

同样从源码中复制SpringBootJoranConfigurator及其相关类，修改了部分代码，主要修改了`SpringPropertyModelHandler`的实现。


```java
/*
 * Copyright 2012-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.logging.logback;

import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.ElementSelector;
import ch.qos.logback.core.joran.spi.RuleStore;
import ch.qos.logback.core.model.Model;
import ch.qos.logback.core.model.processor.DefaultProcessor;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.logback.v13.SpringProfileAction;
import org.springframework.boot.logging.logback.v13.SpringProfileIfNestedWithinSecondPhaseElementSanityChecker;
import org.springframework.boot.logging.logback.v13.SpringProfileModel;
import org.springframework.boot.logging.logback.v13.SpringProfileModelHandler;
import org.springframework.boot.logging.logback.v13.SpringPropertyAction;
import org.springframework.boot.logging.logback.v13.SpringPropertyModel;
import org.springframework.boot.logging.logback.v13.SpringPropertyModelHandler;

/**
 * Extended version of the Logback {@link JoranConfigurator} that adds additional Spring
 * Boot rules.
 *
 * @author Phillip Webb
 * @author Andy Wilkinson
 */
class SpringBootJoran13Configurator extends JoranConfigurator {

    private final LoggingInitializationContext initializationContext;

    SpringBootJoran13Configurator(LoggingInitializationContext initializationContext) {
        this.initializationContext = initializationContext;
    }

    @Override
    protected void addModelHandlerAssociations(DefaultProcessor defaultProcessor) {
        defaultProcessor.addHandler(
                SpringPropertyModel.class,
                (handlerContext, handlerMic) ->
                        new SpringPropertyModelHandler(this.context, this.initializationContext.getEnvironment()));
        defaultProcessor.addHandler(
                SpringProfileModel.class,
                (handlerContext, handlerMic) ->
                        new SpringProfileModelHandler(this.context, this.initializationContext.getEnvironment()));
        super.addModelHandlerAssociations(defaultProcessor);
    }

    @Override
    public void addElementSelectorAndActionAssociations(RuleStore ruleStore) {
        super.addElementSelectorAndActionAssociations(ruleStore);
        ruleStore.addRule(new ElementSelector("configuration/springProperty"), SpringPropertyAction::new);
        ruleStore.addRule(new ElementSelector("*/springProfile"), SpringProfileAction::new);
        ruleStore.addTransparentPathPart("springProfile");
    }

    @Override
    protected void sanityCheck(Model topModel) {
        super.sanityCheck(topModel);
        performCheck(new SpringProfileIfNestedWithinSecondPhaseElementSanityChecker(), topModel);
    }
}
```