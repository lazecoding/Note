package com.codebase.pattern.create.abstractfactory;

/**
 * @author: lazecoding
 * @date: 2021/3/23 21:32
 * @description:
 */
public class Client {
    public static void main(String[] args) {
        AbstractFactory abstractFactory = new ConcreteFactory1();
        AbstractProductA productA = abstractFactory.createProductA();
        System.out.println(productA.getClass());
        AbstractProductB productB = abstractFactory.createProductB();
        System.out.println(productB.getClass());
        // do something with productA and productB
    }
}
