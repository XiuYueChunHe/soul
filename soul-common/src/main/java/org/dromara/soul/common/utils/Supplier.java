package org.dromara.soul.common.utils;


@FunctionalInterface
public interface Supplier<T> {

    /**
     * Gets a result.
     *
     * @return a result
     */
    T lazyformat(Object... msgs);
}
