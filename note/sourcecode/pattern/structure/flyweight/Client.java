package com.codebase.pattern.structure.flyweight;

/**
 * @author: lazecoding
 * @date: 2021/4/17 22:42
 * @description: 场景类
 */
public class Client {

    public static void main(String[] args) {
        FlyweightFactory factory = new FlyweightFactory();
        Flyweight flyweight1 = factory.getFlyweight("a");
        Flyweight flyweight2 = factory.getFlyweight("a");
        Flyweight flyweight3 = factory.getFlyweight("b");

        flyweight1.doOperation("x");
        flyweight2.doOperation("y");
        flyweight3.doOperation("z");
    }
}
