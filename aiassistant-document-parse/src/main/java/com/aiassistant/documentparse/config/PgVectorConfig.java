package com.aiassistant.documentparse.config;

import com.aiassistant.documentparse.pgmapper.VectorTypeHandler;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Secondary (PostgreSQL + pgvector) datasource + session factory.
 * Vector mappers under {@code .pgmapper} (ChunkVector) bind here.
 */
@Configuration
@MapperScan(basePackages = "com.aiassistant.documentparse.pgmapper",
        sqlSessionFactoryRef = "pgSqlSessionFactory")
public class PgVectorConfig {

    @Bean("pgDataSourceProperties")
    @ConfigurationProperties(prefix = "spring.pg-datasource")
    public DataSourceProperties pgDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean("pgDataSource")
    @ConfigurationProperties(prefix = "spring.pg-datasource.hikari")
    public DataSource pgDataSource(
            @Qualifier("pgDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean("pgSqlSessionFactory")
    public SqlSessionFactory pgSqlSessionFactory(@Qualifier("pgDataSource") DataSource dataSource)
            throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);

        factory.setTypeHandlers(new VectorTypeHandler());

        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setBanner(false);
        factory.setGlobalConfig(globalConfig);

        return factory.getObject();
    }
}
