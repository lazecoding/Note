package com.codebase.pattern.behavior.mediator;

/**
 * @author: lazecoding
 * @date: 2021/4/4 19:48
 * @description: 抽象同事类
 */
public abstract class Colleague {
    protected Mediator mediator;
    public Colleague(Mediator _mediator){
        this.mediator = _mediator;
    }
}
