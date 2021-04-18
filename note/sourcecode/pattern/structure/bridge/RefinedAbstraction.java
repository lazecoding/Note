package com.codebase.pattern.structure.bridge;

/**
 * @author: lazecoding
 * @date: 2021/4/18 14:42
 * @description:
 */
public class RefinedAbstraction extends Abstraction {

    public RefinedAbstraction(Implementor _imp) {
        super(_imp);
    }

    @Override
    public void request() {
        /*
         * 业务处理...
         */
        super.request();
        super.getImp().doAnything();
    }
}
