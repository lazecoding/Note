package com.codebase.pattern.structure.flyweight;

/**
 * @author: lazecoding
 * @date: 2021/4/17 22:39
 * @description: 抽象享元
 */
public interface Flyweight {

    /**
     * 业务操作
     *
     * @param extrinsicState
     */
    void doOperation(String extrinsicState);
}
