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

package org.dromara.soul.web.plugin.function;

import com.alibaba.fastjson.JSON;
import org.dromara.soul.common.constant.Constants;
import org.dromara.soul.common.dto.convert.RateLimiterHandle;
import org.dromara.soul.common.dto.zk.RuleZkDTO;
import org.dromara.soul.common.dto.zk.SelectorZkDTO;
import org.dromara.soul.common.enums.PluginEnum;
import org.dromara.soul.common.enums.PluginTypeEnum;
import org.dromara.soul.common.result.SoulResult;
import org.dromara.soul.common.utils.GsonUtil;
import org.dromara.soul.common.utils.JsonUtils;
import org.dromara.soul.common.utils.LogUtils;
import org.dromara.soul.common.utils.U;
import org.dromara.soul.web.cache.ZookeeperCacheManager;
import org.dromara.soul.web.plugin.AbstractSoulPlugin;
import org.dromara.soul.web.plugin.SoulPluginChain;
import org.dromara.soul.web.plugin.ratelimter.RedisRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * RateLimiter Plugin.
 *
 * @author xiaoyu(Myth)
 */
public class RateLimiterPlugin extends AbstractSoulPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger(RateLimiterPlugin.class);

    private final RedisRateLimiter redisRateLimiter;

    /**
     * Instantiates a new Rate limiter plugin.
     *
     * @param zookeeperCacheManager the zookeeper cache manager
     * @param redisRateLimiter      the redis rate limiter
     */
    public RateLimiterPlugin(final ZookeeperCacheManager zookeeperCacheManager,
                             final RedisRateLimiter redisRateLimiter) {
        super(zookeeperCacheManager);
        this.redisRateLimiter = redisRateLimiter;
        LogUtils.info(LOGGER, "实例化RateLimiterPlugin", (a) -> U.lformat(
                "redisRateLimiter", JSON.toJSON(redisRateLimiter),
                "zookeeperCacheManager", JSON.toJSON(zookeeperCacheManager)
        ));
    }

    /**
     * return plugin type.
     *
     * @return {@linkplain PluginTypeEnum}
     */
    @Override
    public PluginTypeEnum getPluginType() {
        return PluginTypeEnum.FUNCTION;
    }

    @Override
    public int getOrder() {
        return PluginEnum.RATE_LIMITER.getCode();
    }

    @Override
    public String getNamed() {
        return PluginEnum.RATE_LIMITER.getName();
    }

    @Override
    protected Mono<Void> doExecute(final ServerWebExchange exchange, final SoulPluginChain chain, final SelectorZkDTO selector, final RuleZkDTO rule) {

        final String handle = rule.getHandle();

        final RateLimiterHandle limiterHandle = GsonUtil.fromJson(handle, RateLimiterHandle.class);

        return redisRateLimiter.isAllowed(rule.getId(), limiterHandle.getReplenishRate(), limiterHandle.getBurstCapacity())
                .flatMap(response -> {
                    if (!response.isAllowed()) {
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse()
                                .bufferFactory()
                                .wrap(Objects.requireNonNull(JsonUtils.toJson(SoulResult.error(Constants.TOO_MANY_REQUESTS)))
                                        .getBytes())));
                    }
                    return chain.execute(exchange);
                });
    }

}
