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

package org.dromara.soul.web.config;

import com.alibaba.fastjson.JSON;
import org.dromara.soul.common.utils.LogUtils;
import org.dromara.soul.common.utils.U;
import org.dromara.soul.web.cache.UpstreamCacheManager;
import org.dromara.soul.web.cache.ZookeeperCacheManager;
import org.dromara.soul.web.disruptor.publisher.SoulEventPublisher;
import org.dromara.soul.web.filter.ParamWebFilter;
import org.dromara.soul.web.filter.TimeWebFilter;
import org.dromara.soul.web.handler.SoulHandlerMapping;
import org.dromara.soul.web.handler.SoulWebHandler;
import org.dromara.soul.web.plugin.SoulPlugin;
import org.dromara.soul.web.plugin.after.MonitorPlugin;
import org.dromara.soul.web.plugin.after.ResponsePlugin;
import org.dromara.soul.web.plugin.before.GlobalPlugin;
import org.dromara.soul.web.plugin.before.SignPlugin;
import org.dromara.soul.web.plugin.before.WafPlugin;
import org.dromara.soul.web.plugin.function.DividePlugin;
import org.dromara.soul.web.plugin.function.RateLimiterPlugin;
import org.dromara.soul.web.plugin.function.RewritePlugin;
import org.dromara.soul.web.plugin.ratelimter.RedisRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.server.WebFilter;

import java.util.List;
import java.util.stream.Collectors;

/**
 * SoulConfiguration.
 *
 * @author xiaoyu(Myth)
 */
@Configuration
@ComponentScan("org.dromara.soul")
public class SoulConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(SoulConfiguration.class);

    private final ZookeeperCacheManager zookeeperCacheManager;

    private final SoulEventPublisher soulEventPublisher;

    private final RedisRateLimiter redisRateLimiter;

    private final UpstreamCacheManager upstreamCacheManager;

    /**
     * Instantiates a new Soul configuration.
     *
     * @param zookeeperCacheManager the zookeeper cache manager
     * @param soulEventPublisher    the soul event publisher
     * @param redisRateLimiter      the redis rate limiter
     */
    @Autowired(required = false)
    public SoulConfiguration(final ZookeeperCacheManager zookeeperCacheManager,
                             final SoulEventPublisher soulEventPublisher,
                             final RedisRateLimiter redisRateLimiter,
                             final UpstreamCacheManager upstreamCacheManager) {
        this.zookeeperCacheManager = zookeeperCacheManager;
        this.soulEventPublisher = soulEventPublisher;
        this.redisRateLimiter = redisRateLimiter;
        this.upstreamCacheManager = upstreamCacheManager;
        LogUtils.info(LOGGER, "初始化SoulConfiguration", (a) -> U.lformat(
                "zookeeperCacheManager", JSON.toJSON(zookeeperCacheManager),
                "soulEventPublisher", JSON.toJSON(soulEventPublisher),
                "redisRateLimiter", JSON.toJSON(redisRateLimiter),
                "upstreamCacheManager", JSON.toJSON(upstreamCacheManager)
        ));

    }

    /**
     * init global plugin.
     *
     * @return {@linkplain GlobalPlugin}
     */
    @Bean
    public SoulPlugin globalPlugin() {
        LogUtils.info(LOGGER, "实例化GlobalPlugin");
        return new GlobalPlugin();
    }


    /**
     * init sign plugin.
     *
     * @return {@linkplain SignPlugin}
     */
    @Bean
    public SoulPlugin signPlugin() {
        LogUtils.info(LOGGER, "实例化SignPlugin");
        return new SignPlugin(zookeeperCacheManager);
    }

    /**
     * init waf plugin.
     *
     * @return {@linkplain WafPlugin}
     */
    @Bean
    public SoulPlugin wafPlugin() {
        LogUtils.info(LOGGER, "实例化WafPlugin");
        return new WafPlugin(zookeeperCacheManager);
    }

    /**
     * init monitor plugin.
     *
     * @return {@linkplain MonitorPlugin}
     */
    @Bean
    public SoulPlugin monitorPlugin() {
        LogUtils.info(LOGGER, "实例化MonitorPlugin");
        return new MonitorPlugin(soulEventPublisher, zookeeperCacheManager);
    }

    /**
     * init rateLimiterPlugin.
     *
     * @return {@linkplain RateLimiterPlugin}
     */
    @Bean
    public SoulPlugin rateLimiterPlugin() {
        LogUtils.info(LOGGER, "实例化RateLimiterPlugin");
        return new RateLimiterPlugin(zookeeperCacheManager, redisRateLimiter);
    }

    /**
     * init rewritePlugin.
     *
     * @return {@linkplain RewritePlugin}
     */
    @Bean
    public SoulPlugin rewritePlugin() {
        LogUtils.info(LOGGER, "实例化RewritePlugin");
        return new RewritePlugin(zookeeperCacheManager);
    }

    /**
     * init dividePlugin.
     *
     * @return {@linkplain DividePlugin}
     */
    @Bean
    public SoulPlugin dividePlugin() {
        LogUtils.info(LOGGER, "实例化DividePlugin");
        return new DividePlugin(zookeeperCacheManager, upstreamCacheManager);
    }

    /**
     * init responsePlugin.
     *
     * @return {@linkplain ResponsePlugin}
     */
    @Bean
    public SoulPlugin responsePlugin() {
        LogUtils.info(LOGGER, "实例化ResponsePlugin");
        return new ResponsePlugin();
    }

    /**
     * init SoulWebHandler.
     *
     * @param plugins this plugins is All impl SoulPlugin.
     * @return {@linkplain SoulWebHandler}
     */
    @Bean
    public SoulWebHandler soulWebHandler(final List<SoulPlugin> plugins) {
        LogUtils.info(LOGGER, "实例化SoulWebHandler");
        final List<SoulPlugin> soulPlugins = plugins.stream()
                .sorted((m, n) -> {
                    if (m.getPluginType().equals(n.getPluginType())) {
                        return m.getOrder() - n.getOrder();
                    } else {
                        return m.getPluginType().getName().compareTo(n.getPluginType().getName());
                    }
                }).collect(Collectors.toList());
        return new SoulWebHandler(soulPlugins);
    }

    /**
     * init  SoulHandlerMapping.
     *
     * @param soulWebHandler {@linkplain SoulWebHandler}
     * @return {@linkplain SoulHandlerMapping}
     */
    @Bean
    public SoulHandlerMapping soulHandlerMapping(final SoulWebHandler soulWebHandler) {
        LogUtils.info(LOGGER, "实例化SoulHandlerMapping");
        return new SoulHandlerMapping(soulWebHandler);
    }

    /**
     * init param web filter.
     *
     * @return {@linkplain ParamWebFilter}
     */
    @Bean
    @Order(1)
    public WebFilter paramWebFilter() {
        LogUtils.info(LOGGER, "实例化ParamWebFilter");
        return new ParamWebFilter();
    }

    /**
     * init time web filter.
     *
     * @return {@linkplain TimeWebFilter}
     */
    @Bean
    @Order(2)
    @ConditionalOnProperty(name = "soul.timeVerify.enabled", matchIfMissing = true)
    public WebFilter timeWebFilter() {
        LogUtils.info(LOGGER, "实例化TimeWebFilter");
        return new TimeWebFilter();
    }
}
