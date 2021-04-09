package com.codebase.pattern.structure.adapter;

/**
 * @author: lazecoding
 * @date: 2021/4/9 22:09
 * @description: 目标角色实现类
 */
public class ConcreteTarget implements Target {
    @Override
    public void request() {
        System.out.println("ConcreteTarget request ...");
    }
}
