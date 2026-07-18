package com.ingenieriadesoftware.EstoNoEsTrello.testutils;

import com.ingenieriadesoftware.EstoNoEsTrello.model.Block;
import com.ingenieriadesoftware.EstoNoEsTrello.model.Card;
import com.ingenieriadesoftware.EstoNoEsTrello.model.User;
import com.ingenieriadesoftware.EstoNoEsTrello.model.WorkSpace;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Test Data Builder mencionado en el Plan de Pruebas (sección 6.1, CP-U-05)
 * como una utilidad "que hoy no existe en el proyecto y debe crearse".
 * <p>
 * Construye jerarquías User -> WorkSpace -> Block -> Card con IDs
 * determinísticos (no aleatorios), para que las pruebas de rendimiento
 * sean reproducibles y no dependan de UUID.randomUUID().
 * <p>
 * IMPORTANTE: se usan siempre los constructores que reciben un id explícito
 * (no nulo) para evitar disparar el contador estático idCounter de
 * Block/Card/WorkSpace, y el constructor de 2 argumentos de User (que NO
 * dispara lectura de disco) para evitar acoplar estas pruebas unitarias a
 * UserJsonController.
 */
public final class TestDataBuilder {

    private TestDataBuilder() {
    }

    public static Card buildCard(long id) {
        return new Card(id, "Tarjeta " + id, "Descripcion " + id,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 15));
    }

    public static Block buildBlockWithCards(long id, int cardCount) {
        ArrayList<Card> cards = new ArrayList<>();
        for (int i = 0; i < cardCount; i++) {
            // ids únicos combinando el id del bloque con el índice de tarjeta
            cards.add(buildCard(id * 1_000L + i));
        }
        return new Block(id, "Bloque " + id, cards);
    }

    public static WorkSpace buildWorkSpaceWithBlocks(long id, int blockCount, int cardsPerBlock) {
        ArrayList<Block> blocks = new ArrayList<>();
        for (int i = 0; i < blockCount; i++) {
            blocks.add(buildBlockWithCards(id * 100L + i, cardsPerBlock));
        }
        return new WorkSpace(id, "Espacio " + id, "Descripcion espacio " + id, blocks);
    }

    /**
     * Construye un User (en memoria, sin tocar Users.json) con la cantidad
     * de WorkSpaces/Blocks/Cards indicada. Útil para CP-U-05, donde el
     * volumen máximo del ERS es 15 WorkSpace x 50 Block x 50 Card.
     */
    public static User buildUserWithWorkspaces(String email, int workspaceCount,
                                                int blocksPerWorkspace, int cardsPerBlock) {
        ArrayList<WorkSpace> workspaces = new ArrayList<>();
        for (int i = 0; i < workspaceCount; i++) {
            workspaces.add(buildWorkSpaceWithBlocks(i + 1L, blocksPerWorkspace, cardsPerBlock));
        }
        User user = new User(email, "Password123!"); // constructor sin lectura de disco
        user.setWorkspaces(workspaces);
        return user;
    }

    /**
     * Construye N usuarios simples (sin workspaces) para las pruebas de
     * volumen de CP-U-01 (0, 10, 100, 1000 usuarios).
     */
    public static List<User> buildSimpleUsers(int count) {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            User user = new User("usuario" + i + "@carga.test", "Password123!");
            user.setWorkspaces(new ArrayList<>());
            users.add(user);
        }
        return users;
    }
}
