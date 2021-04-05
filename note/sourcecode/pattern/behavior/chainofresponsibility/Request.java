package com.codebase.pattern.behavior.chainofresponsibility;

/**
 * @author: lazecoding
 * @date: 2021/4/5 17:31
 * @description:
 */
public class Request {

    private Level level;

    public Request(Level level) {
        this.level = level;
    }

    public Level getRequestLevel(){
        return level;
    }

}