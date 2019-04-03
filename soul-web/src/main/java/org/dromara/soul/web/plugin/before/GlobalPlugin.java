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

package org.dromara.soul.web.plugin.before;

import com.alibaba.fastjson.JSON;
import org.dromara.soul.common.enums.PluginEnum;
import org.dromara.soul.common.enums.PluginTypeEnum;
import org.dromara.soul.common.utils.LogUtils;
import org.dromara.soul.common.utils.U;
import org.dromara.soul.web.handler.SoulWebHandler;
import org.dromara.soul.web.plugin.SoulPlugin;
import org.dromara.soul.web.plugin.SoulPluginChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * GlobalPlugin.
 *
 * @author xiaoyu(Myth)
 */
public class GlobalPlugin implements SoulPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalPlugin.class);

    /**
     * Process the Web request and (optionally) delegate to the next
     * {@code WebFilter} through the given {@link SoulPluginChain}.
     *
     * @param exchange the current server exchange
     * @param chain    provides a way to delegate to the next filter
     * @return {@code Mono<Void>} to indicate when request processing is complete
     */
    @Override
    public Mono<Void> execute(final ServerWebExchange exchange, final SoulPluginChain chain) {
        Mono<Void> result = chain.execute(exchange);
        LogUtils.debug(LOGGER, "执行责任链插件", (a) -> U.lformat("ServerWebExchange", JSON.toJSON(exchange), "SoulPluginChain", JSON.toJSON(chain), "result", JSON.toJSON(result)));
        return result;
    }

    /**
     * return plugin type.
     *
     * @return {@linkplain PluginTypeEnum}
     */
    @Override
    public PluginTypeEnum getPluginType() {
        return PluginTypeEnum.BEFORE;
    }

    @Override
    public int getOrder() {
        return PluginEnum.GLOBAL.getCode();
    }

    @Override
    public String getNamed() {
        return PluginEnum.GLOBAL.getName();
    }
}
