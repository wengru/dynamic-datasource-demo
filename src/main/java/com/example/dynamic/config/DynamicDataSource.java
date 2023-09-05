package com.example.dynamic.config;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicDataSource extends AbstractRoutingDataSource {

    @Value("${datasource.connection.name}")
    private String connectionMapName;

    @Value("${spring.cloud.nacos.config.group}")
    private String group;

    @Value("${spring.cloud.nacos.config.namespace}")
    private String namespace;

    @Qualifier(value = "defaultDataSource")
    @Autowired
    private DataSource defaultDataSource;

    /**
     * 存放所有数据源,其实 key = dataSourceKey , value = DataSource
     */
    private final ConcurrentHashMap<Object, Object> dataSourceMap = new ConcurrentHashMap<>(64);

    /**
     * 主要用于获取当前使用的数据源的key
     * @return
     */
    @Override
    protected Object determineCurrentLookupKey() {
        String sourceKey = DynamicDataSourceContextHolder.peek();
        if (StringUtils.isEmpty(sourceKey)) {
            throw new RuntimeException("dynamic datasource cannot be null...");
        } else if (!dataSourceMap.containsKey(sourceKey)) {
            throw new RuntimeException("dynamic datasource not found...");
        } else {
            System.out.println("采用动态数据源,dataSourceKey: " + sourceKey);
            return sourceKey;
        }
    }

    @PostConstruct
    public void postConstructProcess() {
        loadDataSourceConfig();
        try{
            // 把数据源配置转换为数据源交给spring管理
            // 项目启动时放一条demo数据源
            Class demoDataSourceType = Class.forName("com.zaxxer.hikari.HikariDataSource");
            DataSource demoDataSource = DataSourceBuilder.create()
                    .driverClassName("com.mysql.cj.jdbc.Driver")
                    .username("root")
                    .password("123456")
                    .url("jdbc:mysql://localhost:3306/minhow_first?useUnicode=true&useSSL=false&serverTimezone=Asia/Shanghai&autoReconnect=true&characterEncoding=utf8")
                    .type(demoDataSourceType)
                    .build();
            dataSourceMap.put("demo", demoDataSource);
            // 设置默认数据源和配置的数据源，在afterPropertiesSet方法会刷新数据源
            super.setDefaultTargetDataSource(defaultDataSource);
            super.setTargetDataSources(dataSourceMap);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * 项目启动后支持动态刷新数据源
     */
    public void refreshDataSource() {
        loadDataSourceConfig();
        super.setTargetDataSources(dataSourceMap);
        super.afterPropertiesSet();
    }

    /**
     * 从nacos上加载数据源配置
     * @return
     */
    public void loadDataSourceConfig() {
        Map<String, Object> dataSourceInfoMap = null;
        try {
            // 从nacos上读取数据源配置
            // 可以配置一个统一的域名，域名到ip的映射各环境在host文件里面配置
            String serverAddr = "localhost:8848";
            Properties properties = new Properties();
            properties.put("serverAddr", serverAddr);
            properties.put("namespace", namespace);
            ConfigService configService = NacosFactory.createConfigService(properties);
            String content = configService.getConfig(connectionMapName, group, 5000);
            System.out.println("从nacos上读取到的数据源信息：");
            System.out.println(content);
            Yaml yaml = new Yaml();
            dataSourceInfoMap = yaml.load(content);

            // 从nacos上读取到的数据源配置
            for (String key : dataSourceInfoMap.keySet()) {
                Map<String, String> dataSourceInfo = ((Map) dataSourceInfoMap.get(key));
                Class dataSourceType = Class.forName(dataSourceInfo.get("type"));
                DataSource dataSource = DataSourceBuilder.create()
                        .driverClassName(dataSourceInfo.get("driver-class-name"))
                        .username(dataSourceInfo.get("username"))
                        .password(String.valueOf(dataSourceInfo.get("password")))
                        .url(dataSourceInfo.get("url"))
                        .type(dataSourceType)
                        .build();
                dataSourceMap.put(key, dataSource);
            }
        } catch (NacosException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

}
