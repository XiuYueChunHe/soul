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

package org.dromara.soul.web.cache;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.collections4.CollectionUtils;
import org.dromara.soul.common.dto.convert.DivideUpstream;
import org.dromara.soul.common.dto.zk.SelectorZkDTO;
import org.dromara.soul.common.exception.ExceptionUtil;
import org.dromara.soul.common.utils.GsonUtil;
import org.dromara.soul.common.utils.LogUtils;
import org.dromara.soul.common.utils.U;
import org.dromara.soul.common.utils.UrlUtils;
import org.dromara.soul.web.concurrent.SoulThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

/**
 * this is divide  http url upstream.
 *
 * @author xiaoyu
 */
@Component
public class UpstreamCacheManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(UpstreamCacheManager.class);
    private static final BlockingQueue<SelectorZkDTO> BLOCKING_QUEUE = new LinkedBlockingQueue<>(1024);
    private static final int MAX_THREAD = Runtime.getRuntime().availableProcessors() << 1;
    private static final Map<String, List<DivideUpstream>> UPSTREAM_MAP = Maps.newConcurrentMap();
    private static final Map<String, List<DivideUpstream>> SCHEDULED_MAP = Maps.newConcurrentMap();
    @Value("${soul.upstream.delayInit:30}")
    private Integer delayInit;
    @Value("${soul.upstream.scheduledTime:10}")
    private Integer scheduledTime;

    public static Logger getLOGGER() {
        return LOGGER;
    }

    public static BlockingQueue<SelectorZkDTO> getBlockingQueue() {
        return BLOCKING_QUEUE;
    }

    public static int getMaxThread() {
        return MAX_THREAD;
    }

    public static Map<String, List<DivideUpstream>> getUpstreamMap() {
        return UPSTREAM_MAP;
    }

    public static Map<String, List<DivideUpstream>> getScheduledMap() {
        return SCHEDULED_MAP;
    }

    /**
     * Remove by ruleId.
     *
     * @param ruleId the ruleId
     */
    static void removeByKey(final String ruleId) {
        LogUtils.debug(LOGGER, "根据ruleId移除UPSTREAM_MAP中缓存的DivideUpstream", (a) -> U.lformat("ruleId", ruleId));
        UPSTREAM_MAP.remove(ruleId);
    }

    /**
     * Submit.
     *
     * @param selectorZkDTO the selector zk dto
     */
    static void submit(final SelectorZkDTO selectorZkDTO) {
        try {
            LogUtils.debug(LOGGER, "向UpstreamCacheManager注册SelectorZkDTO", (a) -> U.lformat("pluginName", selectorZkDTO.getPluginName(), "selectorZkDTO", JSON.toJSON(selectorZkDTO)));
            BLOCKING_QUEUE.put(selectorZkDTO);
        } catch (InterruptedException e) {
            LOGGER.error(e.getMessage());
        }
    }

    public Integer getDelayInit() {
        return delayInit;
    }

    public Integer getScheduledTime() {
        return scheduledTime;
    }

    /**
     * 功能说明：根据 selectorId 获取后续服务列表
     * Find upstream list by selector id list.
     *
     * @param selectorId the selector id
     * @return the list
     * Author：spring
     * Date：2019-03-30 09:03
     */
    public List<DivideUpstream> findUpstreamListBySelectorId(final String selectorId) {
        List<DivideUpstream> divideUpstreams = UPSTREAM_MAP.get(selectorId);
        LogUtils.debug(LOGGER, "根据selectorId获取UpstreamList", (a) -> U.lformat("selectorId", selectorId, "divideUpstreams", JSON.toJSON(divideUpstreams)));
        return divideUpstreams;
    }

    /**
     * Init.
     */
    @PostConstruct
    public void init() {
        synchronized (LOGGER) {
            ExecutorService executorService = new ThreadPoolExecutor(MAX_THREAD, MAX_THREAD,
                    0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(),
                    SoulThreadFactory.create("save-upstream-task", false));

            LogUtils.debug(LOGGER, "UpstreamCacheManager初始化完成,即将初始化SelectorZkDTO提交监听任务线程");

            // 启动数个线程同时监听BLOCKING_QUEUE是否有新的SelectorZkDTO被提交,如果有则向SCHEDULED_MAP和UPSTREAM_MAP添加该SelectorZkDTO
            for (int i = 0; i < MAX_THREAD; i++) {
                executorService.execute(new Worker());
            }

            LogUtils.debug(LOGGER, "UpstreamCacheManager初始化完成,即将初始化DivideUpstream可用性监听线程");
            //定时检查后续调用服务的可用性,同时过滤掉不可用服务
            new ScheduledThreadPoolExecutor(MAX_THREAD,
                    SoulThreadFactory.create("scheduled-upstream-task", false))
                    .scheduleWithFixedDelay(this::scheduled,
                            delayInit, scheduledTime, TimeUnit.SECONDS);
        }
    }

    /**
     * 功能说明：定时检查后续调用服务的可用性,同时过滤掉不可用服务
     * Author：spring
     * Date：2019-03-30 08:49
     */
    private void scheduled() {
        int size = SCHEDULED_MAP.size();
        if (size > 0) {
            LogUtils.debug(LOGGER, String.format("即将检查SCHEDULED_MAP中缓存的DivideUpstream可用性,SCHEDULED_MAP.size() = %d", size));
            SCHEDULED_MAP.forEach((selectorId, divideUpstreamList) -> {
                LogUtils.debug(LOGGER, "检查selectorId对应的DivideUpstream可用性", (a) -> U.lformat("selectorId", selectorId, "List<DivideUpstream>.size", divideUpstreamList.size() + "", "divideUpstreamList", JSON.toJSON(divideUpstreamList)));
                List<DivideUpstream> avaliableDivideUpstreams = check(divideUpstreamList);
                LogUtils.debug(LOGGER, "检查selectorId对应的DivideUpstream可用性", (a) -> U.lformat("selectorId", selectorId, "List<DivideUpstream>.size", avaliableDivideUpstreams.size() + "", "divideUpstreamList", JSON.toJSON(avaliableDivideUpstreams)));
                UPSTREAM_MAP.put(selectorId, avaliableDivideUpstreams);
            });
        } else {
            LogUtils.debug(LOGGER, "SCHEDULED_MAP.size() == 0,没有需要监听的线程");
        }
    }

    /**
     * 功能说明：过滤掉不可用后续调用服务
     * Author：spring
     * Date：2019-03-30 08:48
     */
    private List<DivideUpstream> check(final List<DivideUpstream> upstreamList) {
        List<DivideUpstream> resultList = Lists.newArrayListWithCapacity(upstreamList.size());
        for (DivideUpstream divideUpstream : upstreamList) {
            final boolean pass = UrlUtils.checkUrl(divideUpstream.getUpstreamUrl());
            if (pass) {
                LogUtils.debug(LOGGER, "DivideUpstream可用", (a) -> U.lformat("upstreamUrl", divideUpstream.getUpstreamUrl()));
                resultList.add(divideUpstream);
            } else {
                LogUtils.debug(LOGGER, "DivideUpstream >>> 不可用", (a) -> U.lformat("upstreamUrl", divideUpstream.getUpstreamUrl()));
            }
        }
        return resultList;
    }

    /**
     * Execute.
     *
     * @param selectorZkDTO the selector zk dto
     */
    public void execute(final SelectorZkDTO selectorZkDTO) {
        final List<DivideUpstream> upstreamList = GsonUtil.fromList(selectorZkDTO.getHandle(), DivideUpstream[].class);
        if (CollectionUtils.isNotEmpty(upstreamList)) {
            LogUtils.debug(LOGGER, "Worker监听线程注册zk新提交的SelectorZkDTO", (a) -> U.lformat("selectorZkDTO", JSON.toJSON(selectorZkDTO)));
            SCHEDULED_MAP.put(selectorZkDTO.getId(), upstreamList);
            UPSTREAM_MAP.put(selectorZkDTO.getId(), check(upstreamList));
        }
    }

    /**
     * The type Worker.
     */
    class Worker implements Runnable {

        @Override
        public void run() {
            LogUtils.debug(LOGGER, "Worker线程即将执行register SelectorZkDTO任务");
            registerTask();
        }

        private void registerTask() {
            while (true) {

                try {
                    final SelectorZkDTO selectorZkDTO = BLOCKING_QUEUE.take();
                    Optional.of(selectorZkDTO).ifPresent(UpstreamCacheManager.this::execute);
                } catch (Exception e) {
                    LogUtils.debug(LOGGER, "Worker线程执行register SelectorZkDTO任务失败,错误信息如下:");
                    LogUtils.debug(LOGGER, ExceptionUtil.printStackTraceToString(e));
                }

            }
        }
    }

}
