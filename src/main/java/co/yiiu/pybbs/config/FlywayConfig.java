package co.yiiu.pybbs.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.sql.DataSource;

/**
 * Created by tomoya.
 * Copyright (c) 2018, All Rights Reserved.
 * https://atjiu.github.io
 */
@Configuration
public class FlywayConfig {

    @Resource
    private DataSource dataSource;
    // 迁移脚本位置，默认和原来一样；集成测试会指向转换成 H2 方言后的目录
    @Value("${site.flyway_locations:classpath:db/migration,filesystem:db/migration}")
    private String[] locations;

    @PostConstruct
    @DependsOn("dataSourceHelper")
    public void migrate() {
        Flyway flyway = Flyway.configure().dataSource(dataSource).locations(locations)
                .baselineOnMigrate(true).load();
        flyway.migrate();
    }

}
