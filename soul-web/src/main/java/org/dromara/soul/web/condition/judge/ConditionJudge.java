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

package org.dromara.soul.web.condition.judge;

import cn.hutool.log.StaticLog;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.dromara.soul.common.dto.zk.ConditionZkDTO;
import org.dromara.soul.common.enums.OperatorEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.doublespring.utils.U;

import java.util.Map;
import java.util.Objects;

/**
 * ConditionJudge.
 *
 * @author xiaoyu(Myth)
 */
public class ConditionJudge {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConditionJudge.class);

    private static final Map<String, OperatorJudge> OPERATOR_JUDGE_MAP = Maps.newHashMapWithExpectedSize(3);

    static {
        OPERATOR_JUDGE_MAP.put(OperatorEnum.EQ.getAlias(), new EqOperatorJudge());
        OPERATOR_JUDGE_MAP.put(OperatorEnum.MATCH.getAlias(), new MatchOperatorJudge());
        OPERATOR_JUDGE_MAP.put(OperatorEnum.LIKE.getAlias(), new LikeOperatorJudge());
    }

    /**
     * judge this conditionZkDTO has by pass.
     *
     * @param conditionZkDTO condition data
     * @param realData       realData
     * @return is true pass   is false not pass
     */
    public static Boolean judge(final ConditionZkDTO conditionZkDTO, final String realData) {
        if (Objects.isNull(conditionZkDTO) || StringUtils.isBlank(realData)) {
            StaticLog.debug("判断条件不存在,返回false", U.format("conditionZkDTO", JSON.toJSON(conditionZkDTO), "realData", realData));
            return false;
        }
        return OPERATOR_JUDGE_MAP.get(conditionZkDTO.getOperator())
                .judge(conditionZkDTO, realData);
    }
}
