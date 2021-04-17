package com.codebase.pattern.structure.flyweight;

/**
 * @author: lazecoding
 * @date: 2021/4/17 22:42
 * @description: 具体享元
 */
public class ConcreteFlyweight implements Flyweight {
    /**
     * 内部状态
     */
    private String intrinsicState;

    public ConcreteFlyweight(String intrinsicState) {
        this.intrinsicState = intrinsicState;
    }

    @Override
    public void doOperation(String extrinsicState) {
        System.out.println("Object address: " + System.identityHashCode(this));
        System.out.println("IntrinsicState: " + intrinsicState);
        System.out.println("ExtrinsicState: " + extrinsicState);
    }
}
