package com.codebase.pattern.behavior.chainofresponsibility;

/**
 * @author: lazecoding
 * @date: 2021/4/5 17:30
 * @description: 抽象处理者
 */
public abstract class Handler {
    private Handler nextHandler;

    /**
     * 每个处理者都必须对请求做出处理
     * @param request
     */
    public final void handleMessage(Request request){
        //判断是否是自己的处理级别
        if(this.getHandlerLevel().equals(request.getRequestLevel())){
           this.doRequest(request);
        }else{
            //不属于自己的处理级别
            //判断是否有下一个处理者
            if(this.nextHandler != null){
                this.nextHandler.handleMessage(request);
            }else{
                //没有适当的处理者，业务自行处理
            }
        }
    }

    /**
     * 设置下一个处理者是谁
     * @param _handler
     */
    public void setNext(Handler _handler){
        this.nextHandler = _handler;
    }

    /**
     * 每个处理者都有一个处理级别
     * @return
     */
    protected abstract Level getHandlerLevel();

    /**
     * 每个处理者都必须实现处理任务
     * @param request
     */
    protected abstract void doRequest(Request request);
}
