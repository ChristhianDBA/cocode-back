package com.ayudartrazable.api.notificaciones;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracionNotificaciones {

    @Bean(destroyMethod = "close")
    java.util.concurrent.ExecutorService ejecutorPush() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
