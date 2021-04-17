package com.codebase.pattern.behavior.Interpreter;

import java.util.StringTokenizer;

/**
 * @author: lazecoding
 * @date: 2021/4/17 22:03
 * @description:
 */
public class TerminalExpression extends Expression {

    private String literal = null;

    public TerminalExpression(String str) {
        literal = str;
    }

    @Override
    public boolean interpret(String str) {
        StringTokenizer st = new StringTokenizer(str);
        while (st.hasMoreTokens()) {
            String test = st.nextToken();
            if (test.equals(literal)) {
                return true;
            }
        }
        return false;
    }
}
