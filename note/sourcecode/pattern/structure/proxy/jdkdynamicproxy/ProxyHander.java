package com.codebase.pattern.structure.proxy.jdkdynamicproxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * @author: lazecoding
 * @date: 2021/3/31 21:59
 * @description:
 */
public class ProxyHander implements InvocationHandler {

    /**
     * 被代理的对象
     */
    private Object target;

    /**
     * 通过构造函数传递被代理对象
     *
     * @param target
     */
    public ProxyHander(Object target) {
        this.target = target;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object result = method.invoke(this.target, args);
        if (method.getName().equals("doThing")) {
            System.out.println("ProxyHander doThing ...");
        }
        return result;
    }
}
