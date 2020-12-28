
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class TableParser {
    public static void main(String[] args) throws ClassNotFoundException {
        String[] clazzs = new String[2];
        clazzs[0] = "com.codebase.javaSE.annotation.User";
        clazzs[1] = "com.codebase.javaSE.annotation.Group";
        if (clazzs.length < 1) {
            System.out.println(
                    "No Annotated Classes");
            System.exit(0);
        }
        StringBuffer buffer = new StringBuffer();
        List<String> tableList = new LinkedList<>();
        for (String className : clazzs) {
            Class<?> cl = Class.forName(className);
            Table table = cl.getAnnotation(Table.class);
            if (table == null) {
                System.out.println("No Table Annotations In Class :" + className);
                continue;
            }
            boolean create = table.create();
            if (!create) {
                continue;
            }
            String tableName = table.name();
            // If the name is empty, use the Class name:
            if (tableName.length() < 1) {
                tableName = cl.getName().toLowerCase();
            }
            buffer.append("CREATE TABLE " + tableName + " (\n");
            String engine = table.engine().name();
            HashMap<String, String> map = new HashMap<>(16);
            String primaryKey = "";
            int index = 0;
            for (Field field : cl.getDeclaredFields()) {
                Annotation[] anns =
                        field.getDeclaredAnnotations();
                // System.out.println("field:" + field + " \n   anns:" + Arrays.deepToString(anns));
                if (anns.length < 1) {
                    continue;
                }
                for (Annotation annotation : anns) {
                    String columnName = "";
                    if (annotation instanceof Column) {
                        Column columnAnn = (Column) annotation;
                        columnName = columnAnn.name();
                        // If the name is empty, use the Class name:
                        if (columnName.length() < 1) {
                            columnName = field.getName().toLowerCase();
                        }
                        map.put(field.getName(), columnName);
                        String type = columnAnn.type().name();
                        int length = columnAnn.length();
                        String defaultVal = columnAnn.defaultVal();
                        String defaultSql = "";
                        if (defaultVal.length() > 0) {
                            if (type.equals(ColumnType.INT.name())) {
                                defaultSql = " default " + defaultVal + " ";
                            } else if (type.equals(ColumnType.VARCHAR.name())) {
                                defaultSql = " default '" + defaultVal + "' ";
                            }
                        }
                        boolean notNull = columnAnn.notNull();
                        buffer.append((index > 0 ? ",\n" : "") + "  " + columnName + " " + type + "(" + length + ")"
                                + defaultSql + (notNull == true ? " NOT NULL" : ""));
                        index++;
                    }
                    if (annotation instanceof Id) {
                        primaryKey = field.getName();
                    }
                }
            }
            if (primaryKey.length() > 0) {
                buffer.append(",\n  PRIMARY KEY (" + map.get(primaryKey) + ")");
                map.clear();
            }
            buffer.append("\n)ENGINE=" + engine + " DEFAULT CHARSET=utf8;");
            tableList.add(buffer.toString());
            System.out.println(buffer.toString() + "\n");
            // clean buffer
            buffer.setLength(0);
        }
    }
}
