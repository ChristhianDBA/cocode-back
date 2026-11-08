package com.ayudartrazable.api.comun.bd;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracionBaseDatos implements BeanPostProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(ConfiguracionBaseDatos.class);
    private static final String SUPABASE_DIRECT = "db.kdsdcjsqdlieeojsfjin.supabase.co";
    private static final String SUPABASE_POOLER = "aws-0-us-east-1.pooler.supabase.com";
    private static final String POOLER_USER = "postgres.kdsdcjsqdlieeojsfjin";

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof HikariDataSource ds) {
            String url = ds.getJdbcUrl();
            if (url != null && url.contains(SUPABASE_DIRECT)) {
                String fixedUrl = url.replace(SUPABASE_DIRECT, SUPABASE_POOLER);
                LOG.info("Redirigiendo DataSource de Supabase directo IPv6 a Pooler IPv4: {}", fixedUrl);
                ds.setJdbcUrl(fixedUrl);
                if (ds.getUsername() == null || !ds.getUsername().contains(".")) {
                    ds.setUsername(POOLER_USER);
                }
            }
        }
        return bean;
    }
}
