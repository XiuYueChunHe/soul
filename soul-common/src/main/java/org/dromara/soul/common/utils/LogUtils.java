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

package org.dromara.soul.common.utils;

import org.slf4j.Logger;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * LogUtils.
 *
 * @author xiaoyu
 */
public final class LogUtils {

    private static final LogUtils LOG_UTIL = new LogUtils();

    private LogUtils() {

    }

    /**
     * getInstance.
     *
     * @return LogUtils
     */
    public static LogUtils getInstance() {
        return LOG_UTIL;
    }


    /**
     * debug log.
     *
     * @param logger   logger
     * @param format   format
     * @param supplier {@linkplain Supplier}
     */
    public static void debug(final Logger logger, final String format, final Supplier<Object> supplier) {
        logger.debug(format, supplier.get());
    }

    /**
     * debug log.
     *
     * @param logger   logger
     * @param supplier {@linkplain Supplier}
     */
    public static void debug(final Logger logger, final Supplier<Object> supplier) {
        logger.debug(Objects.toString(supplier.get()));
    }

    /**
     * 功能说明：添加String日志
     * Author：spring
     * Date：2019-04-02 16:28
     */
    public static void debug(final Logger logger, final String message) {
        logger.debug(message);
    }

    /**
     * 功能说明：添加String日志
     * Author：spring
     * Date：2019-04-02 16:28
     */
    public static void debug(final Logger logger, final String message, final String content) {
        logger.debug(String.join(",", message, content));
    }

    /**
     * 功能说明：普通 debug 日志
     * Author：spring
     * Date：2019-04-02 17:14
     */
    public static void debug(final Logger logger, org.dromara.soul.common.utils.Supplier<String> msg) {
        logger.debug(msg.lazyformat());
    }

    /**
     * 功能说明：普通 debug 日志
     * Author：spring
     * Date：2019-04-02 17:14
     */
    public static void debug(final Logger logger, String remark, org.dromara.soul.common.utils.Supplier<String> msg) {
        logger.debug(remark + "," + msg.lazyformat());
    }


    /**
     * 功能说明：普通 info 日志
     * Author：spring
     * Date：2019-04-02 17:14
     */
    public static void info(final Logger logger, org.dromara.soul.common.utils.Supplier<String> msg) {
        logger.info(msg.lazyformat());
    }

    /**
     * 功能说明：普通 info 日志
     * Author：spring
     * Date：2019-04-02 17:14
     */
    public static void info(final Logger logger, String remark, org.dromara.soul.common.utils.Supplier<String> msg) {
        logger.info(remark + "," + msg.lazyformat());
    }

    /**
     * 功能说明：重载方法
     * Author：spring
     * Date：2017-12-15 11:53
     */
    public static void info(final Logger logger, Object... msgs) {
        StringBuilder tmp = new StringBuilder();
        for (Object msg : msgs) {
            if (msg != null) {
                tmp.append(msg.toString()).append(",");
            }
        }
        int len = tmp.length();
        if (len > 0) {
            tmp = tmp.replace(len - 1, len, "");
        }
        logger.info(tmp.toString());
    }


    /**
     * 功能说明：添加String日志
     * Author：spring
     * Date：2019-04-02 16:28
     */
    public static void info(final Logger logger, final String message) {
        logger.info(message);
    }

    /**
     * info log.
     *
     * @param logger   logger
     * @param supplier {@linkplain Supplier}
     */
    public static void info(final Logger logger, final Supplier<Object> supplier) {
        logger.info(Objects.toString(supplier.get()));
    }

    /**
     * error log.
     *
     * @param logger   logger
     * @param format   format
     * @param supplier {@linkplain Supplier}
     */
    public static void error(final Logger logger, final String format, final Supplier<Object> supplier) {
        logger.error(format, supplier.get());
    }

    /**
     * error log.
     *
     * @param logger   logger
     * @param supplier {@linkplain Supplier}
     */
    public static void error(final Logger logger, final Supplier<Object> supplier) {
        logger.error(Objects.toString(supplier.get()));
    }

    /**
     * 功能说明：普通 error 日志
     * Author：spring
     * Date：2019-04-02 17:14
     */
    public static void error(final Logger logger, String remark, org.dromara.soul.common.utils.Supplier<String> msg) {
        logger.error(remark + "," + msg.lazyformat());
    }

    /**
     * warn log.
     *
     * @param logger   logger
     * @param format   format
     * @param supplier {@linkplain Supplier}
     */
    public static void warn(final Logger logger, final String format, final Supplier<Object> supplier) {
        logger.warn(format, supplier.get());
    }

    /**
     * warn log.
     *
     * @param logger   logger
     * @param supplier {@linkplain Supplier}
     */
    public static void warn(final Logger logger, final Supplier<Object> supplier) {
        logger.warn(Objects.toString(supplier.get()));
    }
}
