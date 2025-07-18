package compatibility;


import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "AppUser")
public class User {
    @Id
    private int id;
    private String name;

    public User() {}

    public User(int id, String name) {
        this.id = id;
        this.name = name;
    }

    // getters and setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
