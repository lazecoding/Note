package com.codebase.pattern.structure.flyweight;

import java.util.HashMap;

/**
 * @author: lazecoding
 * @date: 2021/4/17 22:40
 * @description: 享元工厂
 */
public class FlyweightFactory {
    /**
     * 共享容器
     */
    private HashMap<String, Flyweight> flyweights = new HashMap<>();

    /**
     * 获取已存在对象或创建新对象
     * @param intrinsicState
     * @return
     */
    Flyweight getFlyweight(String intrinsicState) {
        if (!flyweights.containsKey(intrinsicState)) {
            Flyweight flyweight = new ConcreteFlyweight(intrinsicState);
            flyweights.put(intrinsicState, flyweight);
        }
        return flyweights.get(intrinsicState);
    }
}
