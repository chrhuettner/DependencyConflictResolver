package compatibility;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.internal.bind.ReflectiveTypeAdapterFactory;

public class UserLoader {
    public static void load(String jsonStr) {
        JsonParser parser = new JsonParser();
        // JsonObject obj = parser.parseString(jsonStr).getAsJsonObject();
        //System.out.println(obj.get("username").getAsString());

        //ReflectiveTypeAdapterFactory factory = new ReflectiveTypeAdapterFactory(null, null, null, null, null);
        //factory.excludeField(null, true);
    }

    public static void main(String[] args) {
        load("{\"username\":\"test\"}");
    }
}