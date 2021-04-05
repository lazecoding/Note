package com.codebase.pattern.behavior.command;

/**
 * @author: lazecoding
 * @date: 2021/4/5 14:42
 * @description: 调用者 Invoker 类
 */
public class Invoker {

    private Command command;

    /**
     *  接收命令
     * @param _command
     */
    public void setCommand(Command _command){
        this.command = _command;
    }

    /**
     * 执行命令
     */
    public void action(){
        this.command.execute();
    }
}
