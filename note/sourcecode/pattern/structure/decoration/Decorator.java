package com.codebase.pattern.structure.decoration;

/**
 * @author: lazecoding
 * @date: 2021/4/6 20:58
 * @description:  抽象装饰者
 */
public abstract class Decorator extends Component {
    private Component component = null;

    /**通过构造函数传递被修饰者
     *
     * @param _component
     */
    public Decorator(Component _component){
        this.component = _component;
    }
    //

    /**
     * 委托给被修饰者执行
     */
    @Override
    public void operate() {
        this.component.operate();
    }
}
