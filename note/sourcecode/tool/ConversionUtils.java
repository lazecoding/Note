package lazecoding.util;

import org.apache.commons.lang3.StringUtils;

/**
 * 进制转换器
 */
public enum ConversionUtils {

    /**
     * 单例
     */
    X;

    /**
     * 乱序 62
     */
    private static final String CHARS = "oNWxUYwrXdCOIj4ck6M8RbiQa3H91pSmZTAh70zquLnKvt2VyEGlBsPJgDe5Ff";

    /**
     * base 62
     */
    private static final int SCALE = 62;

    /**
     * 真 - 最小长度
     */
    private static final int MIN_LENGTH = 1;

    /**
     * 默认的业务使用的最小长度
     */
    private static final int DEFAULT_MIN_LENGTH = 5;

    /**
     * 数字转62进制
     *
     * @param num num
     * @return String
     */
    public String encode62(long num) {
        return encode62(num, DEFAULT_MIN_LENGTH);
    }

    /**
     * 数字转 62 进制
     *
     * @param num  num
     * @param size 最小字符长度
     * @return String
     */
    public String encode62(long num, int size) {
        if (num < 0) {
            return String.valueOf(num);
        }
        size = Math.max(MIN_LENGTH, size);
        StringBuilder builder = new StringBuilder();
        int remainder;
        while (num > SCALE - 1) {
            remainder = Long.valueOf(num % SCALE).intValue();
            builder.append(CHARS.charAt(remainder));
            num = num / SCALE;
        }
        builder.append(CHARS.charAt(Long.valueOf(num).intValue()));
        String value = builder.reverse().toString();
        return StringUtils.leftPad(value, size, '0');
    }

    /**
     * 62 进制转为数字
     *
     * @param string string
     * @return long
     */
    public long decode62(String string) {
        string = string.replaceAll("^0*", "");
        long value = 0;
        char tempChar;
        int tempCharValue;
        for (int i = 0; i < string.length(); i++) {
            tempChar = string.charAt(i);
            tempCharValue = CHARS.indexOf(tempChar);
            value += (long) (tempCharValue * Math.pow(SCALE, string.length() - i - 1));
        }
        return value;
    }
}