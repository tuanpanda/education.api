package com.education.base.config;

import javax.sql.DataSource;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Chan khoi dong TRUOC khi DataSource duoc khoi tao neu thieu DB_USERNAME / DB_PASSWORD (AC-15).
 * Chay khi bean DataSource dau tien duoc tao (truoc init/pool), nen test slice khong co DataSource
 * khong bi anh huong. Logic kiem nam o {@link DatasourceEnvPolicy}.
 */
@Component
public class DatasourceEnvStartupValidator implements BeanPostProcessor, EnvironmentAware {

    private Environment environment;
    private boolean checked;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (!checked && bean instanceof DataSource && environment != null) {
            DatasourceEnvPolicy.validate(environment);
            checked = true;
        }
        return bean;
    }
}