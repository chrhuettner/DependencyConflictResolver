public class UserLoader {
    public void load(String jsonStr) {
        JsonParser parser = new JsonParser();
        JsonObject obj = parser.parse(jsonStr).getAsJsonObject();
        System.out.println(obj.get("username").getAsString());
    }
}