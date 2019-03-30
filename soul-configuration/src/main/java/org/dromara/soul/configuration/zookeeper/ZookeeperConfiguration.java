/*
 *   Licensed to the Apache Software Foundation (ASF) under one or more
 *   contributor license agreements.  See the NOTICE file distributed with
 *   this work for additional information regarding copyright ownership.
 *   The ASF licenses this file to You under the Apache License, Version 2.0
 *   (the "License"); you may not use this file except in compliance with
 *   the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package org.dromara.soul.configuration.zookeeper;

import org.I0Itec.zkclient.ZkClient;
import org.dromara.soul.configuration.zookeeper.serializer.ZkSerializerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ZookeeperConfiguration .
 *
 * @author xiaoyu(Myth)
 */
@Configuration
public class ZookeeperConfiguration {

    /**
     * 功能说明：实例化Zookeeper配置信息保存工具类
     * Zookeeper config zookeeper config.
     *
     * @return the zookeeper config
     * Author：spring
     * Date：2019-03-30 08:25
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.zookeeper")
    public ZookeeperConfig zookeeperConfig() {
        return new ZookeeperConfig();
    }

    /**
     * 功能说明：向spring ioc 注册ZookeeperConfig客户端,这里是实现的自己自定义的客户端,主要是自定义ZkSerializer器
     * register zkClient in spring ioc.
     *
     * @param zookeeperConfig the zookeeper config
     * @return ZkClient {@linkplain ZkClient}
     * Author：spring
     * Date：2019-03-30 08:26
     */
    @Bean
    public ZkClient zkClient(ZookeeperConfig zookeeperConfig) {
        return new ZkClient(zookeeperConfig.getUrl(),
                zookeeperConfig.getSessionTimeout(),
                zookeeperConfig.getConnectionTimeout(),
                ZkSerializerFactory.of(zookeeperConfig.getSerializer()));
    }
}
