package com.codebase.pattern.behavior.mediator;

/**
 * @author: lazecoding
 * @date: 2021/4/4 19:48
 * @description: 具体同事类
 */
public class ConcreteColleague1 extends Colleague {

    /**
     * 通过构造函数传递中介者
     *
     * @param _mediator
     */
    public ConcreteColleague1(Mediator _mediator) {
        super(_mediator);
    }

    // 自有方法 self-method
    public void selfMethod1() {
        //处理自己的业务逻辑
        System.out.println("ConcreteColleague1 selfMethod1 ...");
    }

    // 依赖方法 dep-method
    public void depMethod1() {
        //处理自己的业务逻辑
        //自己不能处理的业务逻辑，委托给中介者处理
        super.mediator.doSomething1();
        System.out.println("ConcreteColleague1 depMethod1 ...");

    }
}