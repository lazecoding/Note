package com.codebase.pattern.behavior.mediator;

/**
 * @author: lazecoding
 * @date: 2021/4/5 14:23
 * @description: 场景类
 */
public class Client {
    public static void main(String[] args) {
        Mediator mediator = new ConcreteMediator();

        ConcreteColleague1 concreteColleague1 = new ConcreteColleague1(mediator);
        ConcreteColleague2 concreteColleague2 = new ConcreteColleague2(mediator);

        mediator.setC1(concreteColleague1);
        mediator.setC2(concreteColleague2);

        mediator.doSomething2();
    }
}
