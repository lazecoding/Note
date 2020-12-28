@Table(name = "user", create = true, engine = EngineType.INNODB)
public class User {

    @Id
    @Column(type = ColumnType.INT, length = 11, notNull = true)
    int id;

    @Column(type = ColumnType.VARCHAR, length = 50, notNull = true)
    String name;

    @Column(name = "u_hobby", type = ColumnType.VARCHAR, length = 255, defaultVal = "no hobbies")
    String hobby;

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

    public String getHobby() {
        return hobby;
    }

    public void setHobby(String hobby) {
        this.hobby = hobby;
    }
}
