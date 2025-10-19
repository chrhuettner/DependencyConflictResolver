package compatibility;


import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;


public class Jackson {

    public static void main(String[] args) {
        // Build SessionFactory
        SessionFactory sessionFactory = new Configuration()
                .configure().buildSessionFactory();

        // Open a session
        Session session = sessionFactory.openSession();

        // Begin transaction
        Transaction transaction = session.beginTransaction();

        // Create and save a new user
        User user = new User(1, "John Doe");
        session.persist(user);

        // Commit transaction
        transaction.commit();

        // Fetch user back
        User fetchedUser = session.get(User.class, 1);
        System.out.println("Fetched User: " + fetchedUser.getName());

        // Close session and factory
        session.close();
        sessionFactory.close();
    }

}
