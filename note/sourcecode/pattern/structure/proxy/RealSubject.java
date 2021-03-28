package com.codebase.pattern.structure.proxy;

/**
 * @author: lazecoding
 * @date: 2021/3/28 18:15
 * @description:
 */
public class RealSubject implements Subject {
    //实现方法
    @Override
    public void request() {
        //业务逻辑处理
        System.out.println("RealSubject request ...");
    }
}