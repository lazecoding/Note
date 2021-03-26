package com.codebase.pattern.create.prototype;

/**
 * @author: lazecoding
 * @date: 2021/3/26 23:00
 * @description:
 */
public class PrototypeClass implements Cloneable {

    /**
     * 覆写父类Object方法
     */
    @Override
    public PrototypeClass clone() {
        PrototypeClass prototypeClass = null;
        try {
            prototypeClass = (PrototypeClass) super.clone();
        } catch (CloneNotSupportedException e) {
            //异常处理
        }
        return prototypeClass;
    }

}
