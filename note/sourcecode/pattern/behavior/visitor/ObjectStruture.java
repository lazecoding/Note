package com.codebase.pattern.behavior.visitor;

/**
 * @author: lazecoding
 * @date: 2021/4/16 21:26
 * @description: 结构对象
 */
public class ObjectStruture {

    /**
     * 对象生成器，这里通过一个工厂方法模式模拟
     *
     * @return
     */
    public static Element createElement() {
        if (Math.random() * 100 > 5) {
            return new ConcreteElement1();
        } else {
            return new ConcreteElement2();
        }
    }
}