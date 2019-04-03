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
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.I0Itec.zkclient.IZkDataListener;
import org.I0Itec.zkclient.ZkClient;
import org.apache.commons.collections4.CollectionUtils;
import org.dromara.soul.common.constant.ZkPathConstants;
import org.dromara.soul.common.dto.zk.AppAuthZkDTO;
import org.dromara.soul.common.dto.zk.PluginZkDTO;
import org.dromara.soul.common.dto.zk.RuleZkDTO;
import org.dromara.soul.common.dto.zk.SelectorZkDTO;
import org.dromara.soul.common.enums.PluginEnum;
import org.dromara.soul.common.utils.LogUtils;
import org.dromara.soul.common.utils.U;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * this cache data with zookeeper.
 *
 * @author xiaoyu
 */
@Component
@SuppressWarnings("all")
public class ZookeeperCacheManager implements CommandLineRunner, DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(ZookeeperCacheManager.class);

    private static final Map<String, PluginZkDTO> PLUGIN_MAP = Maps.newConcurrentMap();
    private static final Map<String, List<SelectorZkDTO>> SELECTOR_MAP = Maps.newConcurrentMap();
    private static final Map<String, List<RuleZkDTO>> RULE_MAP = Maps.newConcurrentMap();
    private static final Map<String, AppAuthZkDTO> AUTH_MAP = Maps.newConcurrentMap();
    private final ZkClient zkClient;
    @Value("${soul.upstream.time:30}")
    private int upstreamTime;

    /**
     * Instantiates a new Zookeeper cache manager.
     *
     * @param zkClient the zk client
     */
    @Autowired(required = false)
    public ZookeeperCacheManager(final ZkClient zkClient) {
        this.zkClient = zkClient;
    }

    public static Map<String, PluginZkDTO> getPluginMap() {
        return PLUGIN_MAP;
    }

    public static Map<String, List<SelectorZkDTO>> getSelectorMap() {
        return SELECTOR_MAP;
    }

    public static Map<String, List<RuleZkDTO>> getRuleMap() {
        return RULE_MAP;
    }

    public static Map<String, AppAuthZkDTO> getAuthMap() {
        return AUTH_MAP;
    }

    /**
     * acquire AppAuthZkDTO by appKey with AUTH_MAP container.
     *
     * @param appKey this is appKey.
     * @return AppAuthZkDTO {@linkplain AppAuthZkDTO}
     */
    public AppAuthZkDTO findAuthDTOByAppKey(final String appKey) {
        AppAuthZkDTO appAuthZkDTO = AUTH_MAP.get(appKey);
        LogUtils.debug(LOGGER, "根据appKey查询AuthDTO", (c) -> U.lformat("appKey", appKey, "appAuthZkDTO", JSON.toJSON(appAuthZkDTO)));
        return appAuthZkDTO;
    }

    /**
     * acquire PluginZkDTO by pluginName with PLUGIN_MAP container.
     *
     * @param pluginName this is plugin name.
     * @return PluginZkDTO {@linkplain  PluginZkDTO}
     */
    public PluginZkDTO findPluginByName(final String pluginName) {
        PluginZkDTO pluginZkDTO = PLUGIN_MAP.get(pluginName);
        LogUtils.debug(LOGGER, "根据pluginName查询Plugin", (c) -> U.lformat("pluginName", pluginName, "pluginZkDTO", JSON.toJSON(pluginZkDTO)));
        return pluginZkDTO;
    }

    /**
     * acquire SelectorZkDTO list  by pluginName with  SELECTOR_MAP HashMap container.
     *
     * @param pluginName this is plugin name.
     * @return SelectorZkDTO list {@linkplain  SelectorZkDTO}
     */
    public List<SelectorZkDTO> findSelectorByPluginName(final String pluginName) {
        List<SelectorZkDTO> selectorZkDTOS = SELECTOR_MAP.get(pluginName);
        LogUtils.debug(LOGGER, "根据pluginName查询Selector", (c) -> U.lformat("pluginName", pluginName, "selectorZkDTOS", JSON.toJSON(selectorZkDTOS)));
        return selectorZkDTOS;
    }

    /**
     * acquire RuleZkDTO list by selectorId with  RULE_MAP HashMap container.
     *
     * @param selectorId this is selectorId.
     * @return RuleZkDTO list {@linkplain  RuleZkDTO}
     */
    public List<RuleZkDTO> findRuleBySelectorId(final String selectorId) {
        List<RuleZkDTO> ruleZkDTOS = RULE_MAP.get(selectorId);
        LogUtils.debug(LOGGER, "根据selectorId查询Rule", (c) -> U.lformat("selectorId", selectorId, "ruleZkDTOS", JSON.toJSON(ruleZkDTOS)));
        return ruleZkDTOS;
    }

    @Override
    public void run(final String... args) {

        //1)读取zk中缓存的plugin数据节点;
        //2)将zk数据缓存到plugin MAP中;
        //3)并注册zk缓存节点更新和删除事件,同步更新plugin MAP数据;
        loadPluginWatcher();
        loadSelectorWatcher();
        loadRuleWatcher();
        loadAppAuthWatcher();
    }

    /**
     * 功能说明：
     * 1)读取zk中缓存的plugin数据节点;
     * 2)将zk数据缓存到plugin MAP中;
     * 3)并注册zk缓存节点更新和删除事件,同步更新plugin MAP数据;
     * <p>
     * Author：spring
     * Date：2019-03-30 10:01
     */
    private void loadPluginWatcher() {
        LogUtils.debug(LOGGER, "从zk加载Plugin节点数据开始");
        Arrays.stream(PluginEnum.values()).forEach(pluginEnum -> {

            LogUtils.debug(LOGGER, String.format("从zk加载Plugin[%s]节点数据", JSON.toJSON(pluginEnum)));

            //初始化zk数据保存节点
            String pluginPath = ZkPathConstants.buildPluginPath(pluginEnum.getName());
            if (!zkClient.exists(pluginPath)) {
                zkClient.createPersistent(pluginPath, true);
            }

            LogUtils.debug(LOGGER, "读取zk节点path", (c) -> U.lformat("pluginPath", pluginPath));

            //将插件数据缓存到PLUGIN_MAP
            PluginZkDTO pluginZkDTO = zkClient.readData(pluginPath);
            Optional.ofNullable(pluginZkDTO).ifPresent(d -> {
                LogUtils.debug(LOGGER, "读取zk节点plugin数据存在,保存到PLUGIN_MAP", (c) -> U.lformat("pluginZkDTO", JSON.toJSON(pluginZkDTO)));
                PLUGIN_MAP.put(pluginEnum.getName(), pluginZkDTO);
            });

            LogUtils.debug(LOGGER, "监听zk节点plugin数据更新操作");

            //订阅zk相应节点数据更新事件
            zkClient.subscribeDataChanges(pluginPath, new IZkDataListener() {

                //如果数据更新,则更新plugin缓存MAP
                @Override
                public void handleDataChange(final String dataPath, final Object data) {
                    //如果数据更新,则更新plugin缓存MAP
                    LogUtils.debug(LOGGER, "zk节点plugin数据更新", (c) -> U.lformat("dataPath", dataPath, "pluginZkDTO", JSON.toJSON(data)));
                    Optional.ofNullable(data)
                            .ifPresent(o -> {
                                PluginZkDTO dto = (PluginZkDTO) o;
                                PLUGIN_MAP.put(dto.getName(), dto);
                                LogUtils.debug(LOGGER, "zk节点plugin更新数据保存到PLUGIN_MAP", (c) -> U.lformat("pluginZkDTO", JSON.toJSON(dto)));
                            });
                }

                //如果数据节点被删除,则删除相应plugin缓存
                @Override
                public void handleDataDeleted(final String dataPath) {
                    PluginZkDTO remove = PLUGIN_MAP.remove(pluginEnum.getName());
                    LogUtils.debug(LOGGER, "zk节点plugin数据删除,同步删除PLUGIN_MAP中缓存数据", (c) -> U.lformat("dataPath", dataPath, "pluginZkDTO", JSON.toJSON(remove)));
                }
            });

        });
    }

    private void loadSelectorWatcher() {

        LogUtils.debug(LOGGER, "从zk加载Selector节点数据开始");

        Arrays.stream(PluginEnum.values()).forEach(pluginEnum -> {

            LogUtils.debug(LOGGER, String.format("从zk加载Selector节点数据,所属plugin[%s]", JSON.toJSON(pluginEnum)));

            //获取选择器的节点
            String pluginEnumName = pluginEnum.getName();
            String selectorParentPath = ZkPathConstants.buildSelectorParentPath(pluginEnumName);
            LogUtils.debug(LOGGER, "读取zk节点path", (c) -> U.lformat("selectorParentPath", selectorParentPath));

            if (!zkClient.exists(selectorParentPath)) {
                zkClient.createPersistent(selectorParentPath, true);
            }

            final List<String> selectorPathList = zkClient.getChildren(selectorParentPath);

            if (CollectionUtils.isNotEmpty(selectorPathList)) {
                LogUtils.debug(LOGGER, "初始化监听所有Selector节点数据,并注册每个Selector节点数据监听器", (a) -> U.lformat("pluginEnumName", pluginEnumName, "selectorPathList", JSON.toJSON(selectorPathList)));
                selectorPathList.forEach(selectorPath -> {
                    String selectorRealPath = buildRealPath(selectorParentPath, selectorPath);
                    LogUtils.debug(LOGGER, "从zk获取Selector节点数据", (a) -> U.lformat("selectorPath", JSON.toJSON(selectorPath), "selectorRealPath", selectorRealPath));
                    final SelectorZkDTO selectorZkDTO = zkClient.readData(selectorRealPath);
                    Optional.ofNullable(selectorZkDTO)
                            .ifPresent(dto -> {
                                final String pluginName = dto.getPluginName();
                                //更新某个插件的SelectorZkDTO信息,每个插件都有多个SelectorZkDTO,并且设置plugin所有SelectorZkDTO的先后顺序;
                                LogUtils.debug(LOGGER, "Selector节点数据存在", (a) -> U.lformat("pluginEnumName", pluginEnumName, "selectorZkDTO", JSON.toJSON(selectorZkDTO)));
                                setSelectorMapByPluginName(pluginName, dto);
                            });
                    //监听Selector数据更新、删除操作事件
                    subscribeSelectorDataChanges(selectorRealPath);
                });
            }

            //监听Selector 子节点数据更新、删除操作事件
            LogUtils.debug(LOGGER, "注册当前plugin所属的所有Selector节点数据更新事件", (a) -> U.lformat("pluginEnumName", pluginEnumName, "selectorParentPath", selectorParentPath));

            zkClient.subscribeChildChanges(selectorParentPath, (parentPath, selectorsPath) -> {
                if (CollectionUtils.isNotEmpty(selectorsPath)) {
                    LogUtils.debug(LOGGER, "监听到当前plugin所属的Selector节点数据发生更新", (a) -> U.lformat("pluginEnumName", pluginEnumName, "parentPath", parentPath, "selectorsPath", JSON.toJSON(selectorsPath)));
                    // TODO: 2019-03-30 这里 unsubscribePath 方法需要结合zk数据仔细研究一下功能
                    final List<String> unsubscribePath = unsubscribePath(selectorPathList, selectorsPath);
                    unsubscribePath.stream().map(selectorPath -> buildRealPath(parentPath, selectorPath))
                            .forEach(this::subscribeSelectorDataChanges);
                }
            });

        });
    }

    private void loadRuleWatcher() {

        LogUtils.debug(LOGGER, "从zk加载Rule节点数据开始");

        Arrays.stream(PluginEnum.values()).forEach(pluginEnum -> {

            LogUtils.debug(LOGGER, String.format("从zk加载Rule节点数据,所属plugin[%s]", JSON.toJSON(pluginEnum)));
            String pluginEnumName = pluginEnum.getName();
            final String ruleParent = ZkPathConstants.buildRuleParentPath(pluginEnumName);
            if (!zkClient.exists(ruleParent)) {
                zkClient.createPersistent(ruleParent, true);
            }

            LogUtils.debug(LOGGER, "读取zk节点path", (a) -> U.lformat("ruleParent", ruleParent));

            final List<String> rulePathList = zkClient.getChildren(ruleParent);


            if (CollectionUtils.isNotEmpty(rulePathList)) {
                LogUtils.debug(LOGGER, "初始化监听所有Rule节点数据,并注册每个Rule节点数据监听器", (a) -> U.lformat("pluginEnumName", pluginEnumName, "rulePathList", JSON.toJSON(rulePathList)));
                rulePathList.forEach(rulePath -> {
                    String ruleRealPath = buildRealPath(ruleParent, rulePath);
                    LogUtils.debug(LOGGER, "从zk获取Rule节点数据", (a) -> U.lformat("rulePath", JSON.toJSON(rulePath), "ruleRealPath", ruleRealPath));
                    final RuleZkDTO ruleZkDTO = zkClient.readData(ruleRealPath);
                    Optional.ofNullable(ruleZkDTO)
                            .ifPresent(dto -> {
                                String selectorId = dto.getSelectorId();
                                LogUtils.debug(LOGGER, "Rule节点数据存在", (a) -> U.lformat("pluginEnumName", pluginEnumName, "ruleZkDTO", JSON.toJSON(ruleZkDTO)));
                                setRuleMapByKey(selectorId, ruleZkDTO);
                            });
                    subscribeRuleDataChanges(ruleRealPath);
                });
            }

            zkClient.subscribeChildChanges(ruleParent, (parentPath, currentChilds) -> {
                if (CollectionUtils.isNotEmpty(currentChilds)) {
                    final List<String> unsubscribePath = unsubscribePath(rulePathList, currentChilds);
                    //获取新增的节点数据，并对该节点进行订阅
                    unsubscribePath.stream().map(p -> buildRealPath(parentPath, p))
                            .forEach(this::subscribeRuleDataChanges);
                }
            });
        });
    }

    /**
     * 功能说明：暂不处理该业务
     * Author：spring
     * Date：2019-04-03 16:12
     */
    private void loadAppAuthWatcher() {
        final String appAuthParent = ZkPathConstants.APP_AUTH_PARENT;
        if (!zkClient.exists(appAuthParent)) {
            zkClient.createPersistent(appAuthParent, true);
        }
        final List<String> childrenList = zkClient.getChildren(appAuthParent);
        if (CollectionUtils.isNotEmpty(childrenList)) {
            childrenList.forEach(children -> {
                String realPath = buildRealPath(appAuthParent, children);
                final AppAuthZkDTO appAuthZkDTO = zkClient.readData(realPath);
                Optional.ofNullable(appAuthZkDTO)
                        .ifPresent(dto -> AUTH_MAP.put(dto.getAppKey(), dto));
                subscribeAppAuthDataChanges(realPath);
            });
        }

        zkClient.subscribeChildChanges(appAuthParent, (parentPath, currentChilds) -> {
            if (CollectionUtils.isNotEmpty(currentChilds)) {
                final List<String> unsubscribePath = unsubscribePath(childrenList, currentChilds);
                unsubscribePath.stream().map(children -> buildRealPath(parentPath, children))
                        .forEach(this::subscribeAppAuthDataChanges);
            }
        });
    }

    private String buildRealPath(final String parent, final String children) {
        return parent + "/" + children;
    }

    /**
     * 功能说明：
     * 1)更新某个插件的SelectorZkDTO信息,每个插件都有多个SelectorZkDTO,并且有先后顺序;
     * set  SelectorMap by pluginName.
     *
     * @param pluginName    SELECTOR_MAP pluginName.
     * @param selectorZkDTO data.
     *                      Author：spring
     *                      Date：2019-03-30 10:17
     */
    private void setSelectorMapByPluginName(final String pluginName, final SelectorZkDTO selectorZkDTO) {
        Optional.ofNullable(pluginName)
                .ifPresent(pluginName2 -> {

                    //向UpstreamCacheManager注册SelectorZkDTO
                    if (selectorZkDTO.getPluginName().equals(PluginEnum.DIVIDE.getName())) {
                        LogUtils.debug(LOGGER, "向UpstreamCacheManager注册SelectorZkDTO", (a) -> U.lformat("pluginName", pluginName, "selectorZkDTO", JSON.toJSON(selectorZkDTO)));
                        UpstreamCacheManager.submit(selectorZkDTO);
                    }

                    //向SELECTOR_MAP添加SelectorZkDTO数据
                    LogUtils.debug(LOGGER, "向SELECTOR_MAP添加SelectorZkDTO数据");
                    if (SELECTOR_MAP.containsKey(pluginName2)) {
                        final List<SelectorZkDTO> selectorZkDTOList = SELECTOR_MAP.get(pluginName);
                        LogUtils.debug(LOGGER, "SELECTOR_MAP更新前selectorZkDTOList", (a) -> U.lformat("selectorZkDTOList", JSON.toJSON(selectorZkDTOList)));
                        //由于这里是List,所以更新的时候采用了先过滤掉当前SelectorZkDTO
                        final List<SelectorZkDTO> resultList = selectorZkDTOList.stream()
                                .filter(r -> !r.getId()
                                        .equals(selectorZkDTO.getId()))
                                .collect(Collectors.toList());
                        //再加上最新的SelectorZkDTO
                        resultList.add(selectorZkDTO);
                        //最后根据Selector sort排序后再放入缓存MAP
                        final List<SelectorZkDTO> collect = resultList.stream()
                                .sorted(Comparator.comparing(SelectorZkDTO::getSort))
                                .collect(Collectors.toList());
                        LogUtils.debug(LOGGER, "SELECTOR_MAP更新后selectorZkDTOList", (a) -> U.lformat("selectorZkDTOList", JSON.toJSON(collect)));
                        SELECTOR_MAP.put(pluginName, collect);
                    } else {
                        //直接向SELECTOR_MAP添加SelectorZkDTO数据
                        LogUtils.debug(LOGGER, "SELECTOR_MAP中不存在当前plugin对应的selectorZkDTOList,直接添加");
                        SELECTOR_MAP.put(pluginName, Lists.newArrayList(selectorZkDTO));
                    }
                });
    }

    private void subscribeSelectorDataChanges(final String path) {
        LogUtils.debug(LOGGER, "注册Selector节点数据更新监听事件", (a) -> U.lformat("Selector zk path", path));
        zkClient.subscribeDataChanges(path, new IZkDataListener() {
            @Override
            public void handleDataChange(final String dataPath, final Object data) {
                //处理SelectorZkDTO更新业务
                Optional.ofNullable(data)
                        .ifPresent(d -> {
                            SelectorZkDTO selectorZkDTO = (SelectorZkDTO) d;
                            LogUtils.debug(LOGGER, "Selector节点数据更新事件", (a) -> U.lformat("dataPath", dataPath, "SelectorZkDTO", JSON.toJSON(selectorZkDTO)));
                            //向UpstreamCacheManager注册SelectorZkDTO,用于负载均衡
                            if (selectorZkDTO.getPluginName().equals(PluginEnum.DIVIDE.getName())) {
                                LogUtils.debug(LOGGER, "向UpstreamCacheManager注册SelectorZkDTO", (a) -> U.lformat("pluginName", selectorZkDTO.getPluginName(), "selectorZkDTO", JSON.toJSON(selectorZkDTO)));
                                UpstreamCacheManager.submit(selectorZkDTO);
                            }
                            final String key = selectorZkDTO.getPluginName();
                            final List<SelectorZkDTO> selectorZkDTOList = SELECTOR_MAP.get(key);

                            //根据pluginName更新SelectorZkDTO列表
                            if (CollectionUtils.isNotEmpty(selectorZkDTOList)) {
                                LogUtils.debug(LOGGER, "SELECTOR_MAP更新前selectorZkDTOList", (a) -> U.lformat("selectorZkDTOList", JSON.toJSON(selectorZkDTOList)));

                                final List<SelectorZkDTO> resultList =
                                        selectorZkDTOList.stream().filter(r -> !r.getId().equals(selectorZkDTO.getId())).collect(Collectors.toList());
                                resultList.add(selectorZkDTO);
                                final List<SelectorZkDTO> collect = resultList.stream()
                                        .sorted(Comparator.comparing(SelectorZkDTO::getSort))
                                        .collect(Collectors.toList());
                                LogUtils.debug(LOGGER, "SELECTOR_MAP更新后selectorZkDTOList", (a) -> U.lformat("selectorZkDTOList", JSON.toJSON(collect)));
                                SELECTOR_MAP.put(key, collect);
                            } else {
                                LogUtils.debug(LOGGER, "SELECTOR_MAP中不存在当前plugin对应的selectorZkDTOList,直接添加");
                                SELECTOR_MAP.put(key, Lists.newArrayList(selectorZkDTO));
                            }
                        });
            }

            //处理SelectorZkDTO删除业务
            @Override
            public void handleDataDeleted(final String dataPath) {
                //规定路径 key-id key为selectorId, id为规则id
                final String id = dataPath.substring(dataPath.lastIndexOf("/") + 1);
                final String str = dataPath.substring(ZkPathConstants.SELECTOR_PARENT.length());
                final String key = str.substring(1, str.length() - id.length() - 1);
                Optional.of(key).ifPresent(k -> {
                    final List<SelectorZkDTO> selectorZkDTOList = SELECTOR_MAP.get(k);
                    LogUtils.debug(LOGGER, "Selector节点数据删除事件", (a) -> U.lformat("dataPath", dataPath));
                    //从当前plugin对应的selectorZkDTOList中移除该selectorZkDTO
                    LogUtils.debug(LOGGER, "SELECTOR_MAP删除前selectorZkDTOList", (a) -> U.lformat("selectorZkDTOList", JSON.toJSON(selectorZkDTOList)));
                    selectorZkDTOList.removeIf(e -> e.getId().equals(id));
                    LogUtils.debug(LOGGER, "SELECTOR_MAP删除后selectorZkDTOList", (a) -> U.lformat("selectorZkDTOList", JSON.toJSON(selectorZkDTOList)));
                });
            }
        });
    }

    private List<String> unsubscribePath(final List<String> oldChildrens, final List<String> currentChilds) {
        if (CollectionUtils.isEmpty(oldChildrens)) {
            return currentChilds;
        }
        List<String> latestChild = currentChilds.stream().filter(
                currentChild -> oldChildrens.stream().anyMatch(
                        oldChildren -> !currentChild.equals(oldChildren)
                )
        ).collect(Collectors.toList());
        return latestChild;
    }

    private void setRuleMapByKey(final String selectorId, final RuleZkDTO ruleZkDTO) {
        Optional.ofNullable(selectorId)
                .ifPresent(k -> {
                    LogUtils.debug(LOGGER, "向RULE_MAP添加RuleZkDTO数据", (a) -> U.lformat("selectorId", selectorId, "ruleZkDTO", JSON.toJSON(ruleZkDTO)));
                    if (RULE_MAP.containsKey(k)) {
                        final List<RuleZkDTO> ruleZkDTOList = RULE_MAP.get(selectorId);
                        LogUtils.debug(LOGGER, "RULE_MAP更新前ruleZkDTOList", (a) -> U.lformat("ruleZkDTOList", JSON.toJSON(ruleZkDTOList)));
                        final List<RuleZkDTO> resultList = ruleZkDTOList.stream()
                                .filter(r -> !r.getId()
                                        .equals(ruleZkDTO.getId()))
                                .collect(Collectors.toList());
                        resultList.add(ruleZkDTO);
                        final List<RuleZkDTO> collect = resultList.stream()
                                .sorted(Comparator.comparing(RuleZkDTO::getSort))
                                .collect(Collectors.toList());
                        RULE_MAP.put(selectorId, collect);
                        LogUtils.debug(LOGGER, "RULE_MAP更新后ruleZkDTOList", (a) -> U.lformat("ruleZkDTOList", JSON.toJSON(collect)));
                    } else {
                        LogUtils.debug(LOGGER, "RULE_MAP中不存在当前selectorId对应的ruleZkDTOList,直接添加");
                        RULE_MAP.put(selectorId, Lists.newArrayList(ruleZkDTO));
                    }
                });
    }

    private void subscribeRuleDataChanges(final String ruleRealPath) {
        zkClient.subscribeDataChanges(ruleRealPath, new IZkDataListener() {
            @Override
            public void handleDataChange(final String dataPath, final Object data) {
                Optional.ofNullable(data)
                        .ifPresent(d -> {
                            RuleZkDTO ruleZkDTO = (RuleZkDTO) d;
                            LogUtils.debug(LOGGER, "Rule节点数据更新事件", (a) -> U.lformat("dataPath", dataPath, "ruleZkDTO", JSON.toJSON(ruleZkDTO)));
                            final String selectorId = ruleZkDTO.getSelectorId();
                            final List<RuleZkDTO> ruleZkDTOList = RULE_MAP.get(selectorId);
                            if (CollectionUtils.isNotEmpty(ruleZkDTOList)) {
                                LogUtils.debug(LOGGER, "RULE_MAP更新前ruleZkDTOList", (a) -> U.lformat("ruleZkDTOList", JSON.toJSON(ruleZkDTOList)));
                                final List<RuleZkDTO> resultList = ruleZkDTOList.stream()
                                        .filter(r -> !r.getId()
                                                .equals(ruleZkDTO.getId())).collect(Collectors.toList());
                                resultList.add(ruleZkDTO);
                                final List<RuleZkDTO> collect = resultList.stream()
                                        .sorted(Comparator.comparing(RuleZkDTO::getSort))
                                        .collect(Collectors.toList());
                                RULE_MAP.put(selectorId, collect);
                                LogUtils.debug(LOGGER, "RULE_MAP更新后ruleZkDTOList", (a) -> U.lformat("ruleZkDTOList", JSON.toJSON(collect)));
                            } else {
                                LogUtils.debug(LOGGER, "RULE_MAP中不存在当前selectorId对应的ruleZkDTOList,直接添加");
                                RULE_MAP.put(selectorId, Lists.newArrayList(ruleZkDTO));
                            }
                        });
            }

            @Override
            public void handleDataDeleted(final String dataPath) {
                LogUtils.debug(LOGGER, "Rule节点数据删除事件", (a) -> U.lformat("dataPath", dataPath));
                //规定路径 selectorId-ruleId key为selectorId, id为规则id
                final List<String> list = Splitter.on(ZkPathConstants.SELECTOR_JOIN_RULE)
                        .splitToList(dataPath.substring(dataPath.lastIndexOf("/") + 1));
                final String selectorId = list.get(0);
                final String ruleId = list.get(1);
                LogUtils.debug(LOGGER, "根据ruleId移除UpstreamCacheManager中缓存的DivideUpstream", (a) -> U.lformat("selectorId", selectorId, "ruleId", ruleId));
                UpstreamCacheManager.removeByKey(ruleId);
                Optional.ofNullable(selectorId).ifPresent(selectorIdd -> {
                    final List<RuleZkDTO> ruleZkDTOList = RULE_MAP.get(selectorIdd);
                    LogUtils.debug(LOGGER, "RULE_MAP删除前ruleZkDTOList", (a) -> U.lformat("ruleZkDTOList", JSON.toJSON(ruleZkDTOList)));
                    ruleZkDTOList.removeIf(e -> e.getId().equals(ruleId));
                    LogUtils.debug(LOGGER, "RULE_MAP删除后ruleZkDTOList", (a) -> U.lformat("ruleZkDTOList", JSON.toJSON(ruleZkDTOList)));
                });
            }
        });
    }

    private void subscribeAppAuthDataChanges(final String realPath) {
        zkClient.subscribeDataChanges(realPath, new IZkDataListener() {
            @Override
            public void handleDataChange(final String dataPath, final Object data) {
                Optional.ofNullable(data)
                        .ifPresent(o -> AUTH_MAP.put(((AppAuthZkDTO) o).getAppKey(), (AppAuthZkDTO) o));
            }

            @Override
            public void handleDataDeleted(final String dataPath) {
                final String key = dataPath.substring(ZkPathConstants.APP_AUTH_PARENT.length() + 1);
                AUTH_MAP.remove(key);
            }
        });
    }

    @Override
    public void destroy() {
        zkClient.close();
    }


}
