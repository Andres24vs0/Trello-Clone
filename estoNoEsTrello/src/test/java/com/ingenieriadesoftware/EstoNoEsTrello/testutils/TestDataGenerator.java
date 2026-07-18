package com.ingenieriadesoftware.EstoNoEsTrello.testutils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.ingenieriadesoftware.EstoNoEsTrello.model.Block;
import com.ingenieriadesoftware.EstoNoEsTrello.model.Card;
import com.ingenieriadesoftware.EstoNoEsTrello.model.User;
import com.ingenieriadesoftware.EstoNoEsTrello.model.WorkSpace;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class TestDataGenerator {

    private static final String[] TEST_USERS_PATHS = {
            "target/classes/JSONs/Users.json",
            "target/test-classes/JSONs/Users.json",
            "src/main/resources/JSONs/Users.json",
            "src/test/resources/JSONs/Users.json"
    };

    public static void writeUsersFile(User user) throws IOException {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(LocalDate.class, new JsonSerializer<LocalDate>() {
                    @Override
                    public JsonElement serialize(LocalDate src, Type typeOfSrc, JsonSerializationContext context) {
                        return new JsonPrimitive(src.format(DateTimeFormatter.ISO_LOCAL_DATE));
                    }
                })
                .create();
        ArrayList<User> list = new ArrayList<>();
        list.add(user);
        String json = gson.toJson(list);
        for (String relativePath : TEST_USERS_PATHS) {
            Path path = Paths.get(relativePath);
            Files.createDirectories(path.getParent());
            try (FileWriter fw = new FileWriter(path.toFile())) {
                fw.write(json);
            }
        }
    }

    public static User makeUserWithBlockWithNCards(int n) {
        User user = new User();
        user.setEmail("test@example.com");
        user.setPassword("pw");
        ArrayList<Card> cards = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            cards.add(new Card(null, "card-" + i, "desc" + i));
        }
        Block block = new Block(null, "block-1", cards);
        ArrayList<Block> blocks = new ArrayList<>();
        blocks.add(block);
        WorkSpace ws = new WorkSpace(null, "ws-1", "desc", blocks);
        ArrayList<WorkSpace> wss = new ArrayList<>();
        wss.add(ws);
        user.setWorkspaces(wss);
        return user;
    }

    public static User makeUserWithEmptyWorkspaces() {
        User user = new User();
        user.setEmail("test@example.com");
        user.setPassword("pw");
        user.setWorkspaces(new ArrayList<WorkSpace>());
        return user;
    }

}
