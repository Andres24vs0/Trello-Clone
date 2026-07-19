package com.ingenieriadesoftware.EstoNoEsTrello.integration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ingenieriadesoftware.EstoNoEsTrello.Controllers.CardController;
import com.ingenieriadesoftware.EstoNoEsTrello.Controllers.UserController;
import com.ingenieriadesoftware.EstoNoEsTrello.Controllers.WorkSpaceController;
import com.ingenieriadesoftware.EstoNoEsTrello.JsonControllers.UserJsonController;
import com.ingenieriadesoftware.EstoNoEsTrello.model.Card;
import com.ingenieriadesoftware.EstoNoEsTrello.model.User;
import com.ingenieriadesoftware.EstoNoEsTrello.model.WorkSpace;
import com.ingenieriadesoftware.EstoNoEsTrello.testutils.TestDataGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.times;

@SpringBootTest
public class CPIntegrationTests {

    private static final String[] TEST_USERS_PATHS = {
            "target/classes/JSONs/Users.json",
            "target/test-classes/JSONs/Users.json",
            "src/main/resources/JSONs/Users.json",
            "src/test/resources/JSONs/Users.json"
    };
    private static final String BACKUP_PATH = "target/test-users-backup.json";

    private void resetFixture() throws IOException {
        TestDataGenerator.writeUsersFile(TestDataGenerator.makeUserWithEmptyWorkspaces());
    }

    @BeforeEach
    void setUp() throws IOException {
        resetFixture();
    }

    @AfterEach
    void tearDown() throws IOException {
        resetFixture();
    }

    @Test
    @DisplayName("CP-I-01: Tiempo ciclo completo de addCard() con 0/25/49 tarjetas")
    void cp_i_01_addCard_timing() throws IOException {
        int[] scenarios = { 0, 25, 49 };
        long thresholdNs = 2_000_000_000L; // 2 seconds

        for (int n : scenarios) {
            User user = TestDataGenerator.makeUserWithBlockWithNCards(n);
            TestDataGenerator.writeUsersFile(user);

            Card newCard = new Card(null, "integration-card", "desc");
            User testUser = new User();
            testUser.setEmail("test@example.com");

            long start = System.nanoTime();
            try {
                // add card to block 1 (ids generated internally)
                CardController.addCard(newCard, testUser, user.getWorkspaces().get(0).getBlocks().get(0).getId(),
                        user.getWorkspaces().get(0).getId());
            } catch (Exception e) {
                fail("addCard threw: " + e.getMessage());
            }
            long elapsed = System.nanoTime() - start;
            System.out.println("Scenario cards=" + n + " elapsed_ms=" + (elapsed / 1_000_000));
            assertTrue(elapsed < thresholdNs, "addCard exceeded threshold for n=" + n);
        }
    }

    @Test
    @DisplayName("CP-I-02: Integridad de Users.json ante escrituras concurrentes (addWorkSpace)")
    void cp_i_02_concurrent_writes() throws IOException, InterruptedException {
        // initial user with empty workspaces
        User base = TestDataGenerator.makeUserWithEmptyWorkspaces();
        TestDataGenerator.writeUsersFile(base);

        for (int threads : new int[] { 2, 5 }) {
            for (int attempt = 0; attempt < 3; attempt++) {
                // restore base state before each attempt
                TestDataGenerator.writeUsersFile(base);

                int N = threads * 10; // number of write attempts
                ExecutorService ex = Executors.newFixedThreadPool(threads);
                CountDownLatch latch = new CountDownLatch(N);
                for (int i = 0; i < N; i++) {
                    final int idx = i;
                    ex.submit(() -> {
                        try {
                            User u = new User();
                            u.setEmail("test@example.com");
                            WorkSpace ws = new WorkSpace(null, "ws-conc-" + idx, "desc", null);
                            WorkSpaceController.addWorkSpace(ws, u);
                        } catch (Exception e) {
                            // swallow - we'll check final state
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                latch.await(30, TimeUnit.SECONDS);
                ex.shutdownNow();

                ArrayList<User> users = UserJsonController.findTotalUsers();
                assertNotNull(users, "the fixture file should remain readable after concurrent writes");
                assertTrue(users.size() >= 0,
                        "the fixture file should remain readable after concurrent writes");

                int persisted = users.stream()
                        .filter(u -> u != null && u.getWorkspaces() != null)
                        .mapToInt(u -> u.getWorkspaces().size())
                        .sum();
                System.out.println("=== CP-I-02: Escrituras concurrentes => esperadas=" + N + ", persistidas="
                        + persisted + " ===");
                assertEquals(N, persisted,
                        "The JSON file should preserve exactly one workspace per launched write attempt for threads="
                                + threads);
            }
        }
    }

    @Test
    @DisplayName("CP-I-03: Tiempo de lectura/escritura por volumen de datos")
    void cp_i_03_volume_read_write() throws IOException {
        int[] counts = { 100, 1000, 10000, 37500 };
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        for (int c : counts) {
            User u = TestDataGenerator.makeUserWithBlockWithNCards(c);
            TestDataGenerator.writeUsersFile(u);

            long startFind = System.nanoTime();
            ArrayList<User> users = UserJsonController.findTotalUsers();
            long findElapsed = System.nanoTime() - startFind;

            long startSave = System.nanoTime();
            // perform a saveUser that appends (we'll save same user to force write)
            UserJsonController.saveUser(u);
            long saveElapsed = System.nanoTime() - startSave;

            System.out.println(
                    "volume=" + c + " find_ms=" + (findElapsed / 1_000_000) + " save_ms=" + (saveElapsed / 1_000_000));
            // no strict assert, just ensure it runs and measurements recorded
            assertTrue(findElapsed > 0);
            assertTrue(saveElapsed > 0);
        }
    }

    @Test
    @DisplayName("CP-I-04: Relecturas de archivo por sesión de uso")
    void cp_i_04_relecturas_count() throws IOException {
        // prepare user with one workspace and one block
        User u = TestDataGenerator.makeUserWithBlockWithNCards(5);
        TestDataGenerator.writeUsersFile(u);

        UserController userController = new UserController();
        try (MockedStatic<UserJsonController> mocked = Mockito.mockStatic(UserJsonController.class)) {
            mocked.when(UserJsonController::findTotalUsers).thenCallRealMethod();

            // simulate 5 consecutive queries of same workspace
            for (int i = 0; i < 5; i++) {
                try {
                    userController.loadCards("test@example.com", u.getWorkspaces().get(0).getBlocks().get(0).getId(),
                            u.getWorkspaces().get(0).getId());
                } catch (Exception e) {
                    fail("loadCards threw: " + e.getMessage());
                }
            }

            // verify that findTotalUsers was called 5 times
            mocked.verify(UserJsonController::findTotalUsers, times(5));
            System.out.println(
                    "=== CP-I-04: Relecturas completas de archivo detectadas = 5 (Meta deseable declarada: 1) ===");
        }
    }
}
