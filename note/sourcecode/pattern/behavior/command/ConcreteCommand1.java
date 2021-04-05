package com.codebase.pattern.behavior.command;

/**
 * @author: lazecoding
 * @date: 2021/4/5 14:40
 * @description: 具体命令
 */
public class ConcreteCommand1 extends Command {

    private Receiver receiver;

    /**
     * 构造函数传递接收者
     * @param _receiver
     */
    public ConcreteCommand1(Receiver _receiver) {
        this.receiver = _receiver;
    }

    @Override
    public void execute() {
        //业务处理
        this.receiver.doSomething();
        System.out.println("ConcreteCommand1 execute ...");
    }
}
