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

package org.dromara.soul.web.handler;

import com.alibaba.fastjson.JSON;
import org.dromara.soul.common.utils.LogUtils;
import org.dromara.soul.common.utils.U;
import org.dromara.soul.web.plugin.SoulPlugin;
import org.dromara.soul.web.plugin.SoulPluginChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebHandler;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * this is web handler request starter.
 *
 * @author xiaoyu(Myth)
 */
public final class SoulWebHandler implements WebHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SoulWebHandler.class);

    private List<SoulPlugin> plugins;

    /**
     * Instantiates a new Soul web handler.
     *
     * @param plugins the plugins
     */
    public SoulWebHandler(final List<SoulPlugin> plugins) {
        JSON.toJSON(plugins);
        LogUtils.info(LOGGER, "初始化SoulWebHandler", (a) -> U.lformat("plugins", JSON.toJSON(plugins)));
        this.plugins = plugins;
    }

    /**
     * Handle the web server exchange.
     *
     * @param exchange the current server exchange
     * @return {@code Mono<Void>} to indicate when request handling is complete
     */
    @Override
    public Mono<Void> handle(final ServerWebExchange exchange) {
        LogUtils.debug(LOGGER, "SoulWebHandler接收到请求,即将进入请求处理责任链", (a) -> U.lformat("exchange", JSON.toJSON(exchange), "plugins", JSON.toJSON(plugins)));
        return new DefaultSoulPluginChain(plugins)
                .execute(exchange)
                .doOnError(Throwable::printStackTrace);
    }

    private static class DefaultSoulPluginChain implements SoulPluginChain {

        private final List<SoulPlugin> plugins;
        private int index;

        /**
         * Instantiatesa new Default soul plugin chain.
         *
         * @param plugins the plugins
         */
        DefaultSoulPluginChain(final List<SoulPlugin> plugins) {
            this.plugins = plugins;
        }

        /**
         * Delegate to the next {@code WebFilter} in the chain.
         *
         * @param exchange the current server exchange
         * @return {@code Mono<Void>} to indicate when request handling is complete
         */
        @Override
        public Mono<Void> execute(final ServerWebExchange exchange) {
            if (this.index < plugins.size()) {
                SoulPlugin plugin = plugins.get(this.index++);
                Mono<Void> result = plugin.execute(exchange, this);
                LogUtils.debug(LOGGER, "执行责任链插件", (a) -> U.lformat("plugin", JSON.toJSON(plugin), "result", JSON.toJSON(result)));
                return result;
            } else {
                LogUtils.debug(LOGGER, "责任链插件全部执行完毕");
                return Mono.empty();
            }
        }
    }
}
