package com.codebase.pattern.behavior.Interpreter;

/**
 * @author: lazecoding
 * @date: 2021/4/17 22:04
 * @description:
 */
public class OrExpression extends Expression {
    private Expression expression1 = null;
    private Expression expression2 = null;

    public OrExpression(Expression expression1, Expression expression2) {
        this.expression1 = expression1;
        this.expression2 = expression2;
    }

    @Override
    public boolean interpret(String str) {
        return expression1.interpret(str) || expression2.interpret(str);
    }
}
