package com.codebase.pattern.structure.bridge;

/**
 * @author: lazecoding
 * @date: 2021/4/18 14:42
 * @description:
 */
public class ConcreteImplementor2 implements Implementor{
    @Override
    public void doSomething(){
        //业务逻辑处理
        System.out.println("ConcreteImplementor2 doSomething ...");
    }
    @Override
    public void doAnything(){
        //业务逻辑处理
        System.out.println("ConcreteImplementor2 doAnything ...");
    }
}
