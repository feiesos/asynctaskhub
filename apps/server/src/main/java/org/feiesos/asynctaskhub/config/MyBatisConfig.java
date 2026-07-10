package org.feiesos.asynctaskhub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyBatisConfig {

    @Bean
    public JsonbTypeHandler jsonbTypeHandler() {
        return new JsonbTypeHandler();
    }

    @Bean
    public PgUuidTypeHandler pgUuidTypeHandler() {
        return new PgUuidTypeHandler();
    }

    @Bean
    public PgEnumTypeHandler pgEnumTypeHandler() {
        return new PgEnumTypeHandler();
    }
}
