import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
public class UserLoader {
    public void load(String jsonStr) {
        JsonParser parser = new JsonParser();
        JsonObject obj = parser.parse(jsonStr).getAsJsonObject();
        System.out.println(obj.get("username").getAsString());
    }
}