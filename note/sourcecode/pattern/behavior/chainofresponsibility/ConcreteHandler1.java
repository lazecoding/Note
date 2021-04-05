package com.codebase.pattern.behavior.chainofresponsibility;

/**
 * @author: lazecoding
 * @date: 2021/4/5 17:30
 * @description: 具体处理者
 */
public class ConcreteHandler1 extends Handler {
    /**
     * 定义自己的处理逻辑
     *
     * @param request
     */
    @Override
    protected void doRequest(Request request) {
        //完成处理逻辑
        System.out.println("ConcreteHandler1 doRequest" + request.getRequestLevel() + " ...");
    }

    /**
     * 设置自己的处理级别
     *
     * @return
     */
    @Override
    protected Level getHandlerLevel() {
        //设置自己的处理级别
        return Level.LEVEL1;
    }
}
