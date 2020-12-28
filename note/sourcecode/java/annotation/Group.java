@Table(name = "group", create = true, engine = EngineType.MYISAM)
public class Group {

    @Column(type = ColumnType.INT, length = 11, notNull = true)
    int id;

    @Column(type = ColumnType.VARCHAR, length = 50, notNull = true)
    String name;

    @Column(type = ColumnType.VARCHAR, length = 255)
    String area;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }
}
