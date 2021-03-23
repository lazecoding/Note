package com.codebase.pattern.create.abstractfactory;

/**
 * @author: lazecoding
 * @date: 2021/3/23 21:31
 * @description:
 */
public abstract class AbstractFactory {
    abstract AbstractProductA createProductA();
    abstract AbstractProductB createProductB();
}