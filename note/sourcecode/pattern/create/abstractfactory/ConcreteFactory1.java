package com.codebase.pattern.create.abstractfactory;

/**
 * @author: lazecoding
 * @date: 2021/3/23 21:31
 * @description:
 */
public class ConcreteFactory1 extends AbstractFactory {
    @Override
    AbstractProductA createProductA() {
        return new ProductA1();
    }

    @Override
    AbstractProductB createProductB() {
        return new ProductB1();
    }
}