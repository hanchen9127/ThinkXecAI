package com.thinkxecai.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.thinkxecai.mapper")
public class MyBatisConfig {
}
