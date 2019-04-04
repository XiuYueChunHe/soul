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
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.dromara.soul.common.constant.Constants;
import org.dromara.soul.common.dto.convert.DivideUpstream;
import org.dromara.soul.common.dto.convert.rule.DivideRuleHandle;
import org.dromara.soul.common.dto.zk.RuleZkDTO;
import org.dromara.soul.common.dto.zk.SelectorZkDTO;
import org.dromara.soul.common.enums.PluginEnum;
import org.dromara.soul.common.enums.PluginTypeEnum;
import org.dromara.soul.common.enums.ResultEnum;
import org.dromara.soul.common.enums.RpcTypeEnum;
import org.dromara.soul.common.exception.ExceptionUtil;
import org.dromara.soul.common.utils.GsonUtil;
import org.dromara.soul.common.utils.LogUtils;
import org.dromara.soul.common.utils.U;
import org.dromara.soul.web.balance.LoadBalance;
import org.dromara.soul.web.balance.factory.LoadBalanceFactory;
import org.dromara.soul.web.cache.UpstreamCacheManager;
import org.dromara.soul.web.cache.ZookeeperCacheManager;
import org.dromara.soul.web.plugin.AbstractSoulPlugin;
import org.dromara.soul.web.plugin.SoulPluginChain;
import org.dromara.soul.web.plugin.hystrix.HttpCommand;
import org.dromara.soul.web.plugin.hystrix.HystrixBuilder;
import org.dromara.soul.web.request.RequestDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import rx.Subscription;

import java.util.List;
import java.util.Objects;

/**
 * Divide Plugin.
 *
 * @author xiaoyu(Myth)
 */
public class DividePlugin extends AbstractSoulPlugin {

    /**
     * logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(DividePlugin.class);

    private final UpstreamCacheManager upstreamCacheManager;

    /**
     * Instantiates a new Divide plugin.
     *
     * @param zookeeperCacheManager the zookeeper cache manager
     */
    public DividePlugin(final ZookeeperCacheManager zookeeperCacheManager, final UpstreamCacheManager upstreamCacheManager) {
        super(zookeeperCacheManager);
        this.upstreamCacheManager = upstreamCacheManager;
        LogUtils.info(LOGGER, "实例化DividePlugin", (a) -> U.lformat(
                "zookeeperCacheManager", JSON.toJSON(zookeeperCacheManager),
                "upstreamCacheManager", JSON.toJSON(upstreamCacheManager)
        ));
    }

    @Override
    protected Mono<Void> doExecute(final ServerWebExchange exchange, final SoulPluginChain chain, final SelectorZkDTO selector, final RuleZkDTO rule) {

        LogUtils.debug(LOGGER, "执行DividePlugin", (a) -> U.lformat("ServerWebExchange", JSON.toJSON(exchange), "SoulPluginChain", JSON.toJSON(chain), "selector", JSON.toJSON(selector), "rule", JSON.toJSON(rule)));

        final RequestDTO requestDTO = exchange.getAttribute(Constants.REQUESTDTO);

        final DivideRuleHandle divideRuleHandle = GsonUtil.fromJson(rule.getHandle(), DivideRuleHandle.class);

        if (StringUtils.isBlank(divideRuleHandle.getGroupKey())) {
            divideRuleHandle.setGroupKey(Objects.requireNonNull(requestDTO).getModule());
        }

        if (StringUtils.isBlank(divideRuleHandle.getCommandKey())) {
            divideRuleHandle.setCommandKey(Objects.requireNonNull(requestDTO).getMethod());
        }

        final List<DivideUpstream> divideUpstreams = upstreamCacheManager.findUpstreamListBySelectorId(selector.getId());

        if (CollectionUtils.isEmpty(divideUpstreams)) {
            LogUtils.debug(LOGGER, "DivideUpstream不存在,执行下一个责任链插件");
            return chain.execute(exchange);
        }

        DivideUpstream divideUpstream = null;
        if (divideUpstreams.size() == 1) {
            divideUpstream = divideUpstreams.get(0);
        } else {
            if (StringUtils.isNoneBlank(divideRuleHandle.getLoadBalance())) {
                final LoadBalance loadBalance = LoadBalanceFactory.of(divideRuleHandle.getLoadBalance());
                final String ip = Objects.requireNonNull(exchange.getRequest().getRemoteAddress()).getAddress().getHostAddress();
                divideUpstream = loadBalance.select(divideUpstreams, ip);
            }
        }

        if (Objects.isNull(divideUpstream)) {
            LogUtils.debug(LOGGER, "LoadBalance DivideUpstream不存在,执行下一个责任链插件");
            return chain.execute(exchange);
        }

        HttpCommand command = new HttpCommand(HystrixBuilder.build(divideRuleHandle), exchange, chain, requestDTO, buildRealURL(divideUpstream), divideRuleHandle.getTimeout());
        return Mono.create((MonoSink<Object> monoSink) -> {
            Subscription subscription = command.toObservable().subscribe(monoSink::success, monoSink::error, monoSink::success);
            monoSink.onCancel(subscription::unsubscribe);
            if (command.isCircuitBreakerOpen()) {
                LogUtils.error(LOGGER, "触发熔断风控", (a) -> U.lformat("module", divideRuleHandle.getGroupKey(), "method", divideRuleHandle.getCommandKey(), "maxConcurrentRequests", divideRuleHandle.getMaxConcurrentRequests()));
            }
        }).doOnError(exception -> {
            exchange.getAttributes().put(Constants.CLIENT_RESPONSE_RESULT_TYPE, ResultEnum.ERROR.getName());
            Mono<Void> result = chain.execute(exchange);
            LogUtils.error(LOGGER, "请求后台失败,执行下一个责任链插件", (a) -> U.lformat("exception", ExceptionUtil.printStackTraceToString(exception), "result", JSON.toJSON(result)));
        }).then();
    }

    private String buildRealURL(final DivideUpstream divideUpstream) {
        String protocol = divideUpstream.getProtocol();
        if (StringUtils.isBlank(protocol)) {
            protocol = "http://";
        }
        return protocol + divideUpstream.getUpstreamUrl().trim();
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
        return PluginEnum.DIVIDE.getCode();
    }

    @Override
    public String getNamed() {
        return PluginEnum.DIVIDE.getName();
    }

    /**
     * plugin is execute.
     *
     * @return default false.
     */
    @Override
    public Boolean skip(final ServerWebExchange exchange) {
        final RequestDTO body = exchange.getAttribute(Constants.REQUESTDTO);
        return !Objects.equals(Objects.requireNonNull(body).getRpcType(), RpcTypeEnum.HTTP.getName());
    }

}
