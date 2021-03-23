package com.codebase.pattern.create.abstractfactory;

/**
 * @author: lazecoding
 * @date: 2021/3/23 21:32
 * @description:
 */
public class ConcreteFactory2 extends AbstractFactory {
    @Override
    AbstractProductA createProductA() {
        return new ProductA2();
    }

    @Override
    AbstractProductB createProductB() {
        return new ProductB2();
    }
}
