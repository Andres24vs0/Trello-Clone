package com.ingenieriadesoftware.EstoNoEsTrello.Controllers;

import com.ingenieriadesoftware.EstoNoEsTrello.model.Block;
import com.ingenieriadesoftware.EstoNoEsTrello.model.Card;
import com.ingenieriadesoftware.EstoNoEsTrello.model.User;
import com.ingenieriadesoftware.EstoNoEsTrello.model.WorkSpace;
import com.ingenieriadesoftware.EstoNoEsTrello.testutils.TestDataBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-U-05 — WorkSpaceController.findWorkSpace() / BlockController.findBlock()
 * / CardController.findCard()
 * Plan de Pruebas ST-KANBAN-PERF-PLAN, sección 5.1 / 6.1.
 * <p>
 * Entrada: colecciones en su tamaño máximo permitido por el ERS: 15
 * WorkSpace, 50 Block por WorkSpace, 50 Card por Block.
 * Resultado esperado: el tiempo de búsqueda es proporcional al tamaño de
 * la colección (orden lineal), sin degradación adicional al alcanzar el
 * máximo. Se busca siempre el ÚLTIMO elemento (peor caso de una búsqueda
 * secuencial).
 * <p>
 * NOTA: findWorkSpace/findBlock/findCard operan íntegramente en memoria
 * sobre las listas del objeto User recibido por parámetro (no acceden a
 * UserJsonController ni a disco), por lo que son unidades de prueba
 * puras y no requieren mocks ni fixtures de Users.json.
 * <p>
 * Usa el Test Data Builder creado para este plan (TestDataBuilder), ya
 * que el proyecto no contaba con una utilidad de este tipo.
 */
class FindOperationsScalabilityTest {

    private static final int REPETITIONS = 5_000;
    private static final int WARMUP_REPETITIONS = 500;

    // Tamaño máximo declarado por el ERS
    private static final int MAX_WORKSPACES = 15;
    private static final int MAX_BLOCKS_PER_WORKSPACE = 50;
    private static final int MAX_CARDS_PER_BLOCK = 50;

    @Test
    @DisplayName("CP-U-05: findWorkSpace() escala linealmente hasta 15 WorkSpace (máximo ERS)")
    void findWorkSpace_worstCase_scalesLinearlyUpToErsMaximum() throws IOException {
        double avgSmall = averageFindWorkSpaceNanos(3);
        double avgMax = averageFindWorkSpaceNanos(MAX_WORKSPACES);

        logAndAssertLinearGrowth("findWorkSpace", 3, MAX_WORKSPACES, avgSmall, avgMax);
    }

    @Test
    @DisplayName("CP-U-05: findBlock() escala linealmente hasta 50 Block por WorkSpace (máximo ERS)")
    void findBlock_worstCase_scalesLinearlyUpToErsMaximum() throws IOException {
        double avgSmall = averageFindBlockNanos(5);
        double avgMax = averageFindBlockNanos(MAX_BLOCKS_PER_WORKSPACE);

        logAndAssertLinearGrowth("findBlock", 5, MAX_BLOCKS_PER_WORKSPACE, avgSmall, avgMax);
    }

    @Test
    @DisplayName("CP-U-05: findCard() escala linealmente hasta 50 Card por Block (máximo ERS)")
    void findCard_worstCase_scalesLinearlyUpToErsMaximum() throws IOException {
        double avgSmall = averageFindCardNanos(5);
        double avgMax = averageFindCardNanos(MAX_CARDS_PER_BLOCK);

        logAndAssertLinearGrowth("findCard", 5, MAX_CARDS_PER_BLOCK, avgSmall, avgMax);
    }

    // ---------- Mediciones ----------

    private double averageFindWorkSpaceNanos(int numWorkSpaces) throws IOException {
        User user = TestDataBuilder.buildUserWithWorkspaces("scale-ws@test.com", numWorkSpaces, 1, 1);
        Long lastWorkSpaceId = user.getWorkspaces().get(user.getWorkspaces().size() - 1).getId();

        for (int i = 0; i < WARMUP_REPETITIONS; i++) {
            WorkSpaceController.findWorkSpace(lastWorkSpaceId, user);
        }

        long totalNanos = 0L;
        for (int i = 0; i < REPETITIONS; i++) {
            long start = System.nanoTime();
            WorkSpace found = WorkSpaceController.findWorkSpace(lastWorkSpaceId, user);
            totalNanos += System.nanoTime() - start;
            assertEquals(lastWorkSpaceId, found.getId());
        }
        return totalNanos / (double) REPETITIONS;
    }

    private double averageFindBlockNanos(int numBlocks) throws IOException {
        User user = TestDataBuilder.buildUserWithWorkspaces("scale-block@test.com", 1, numBlocks, 1);
        WorkSpace workSpace = user.getWorkspaces().get(0);
        Long workSpaceId = workSpace.getId();
        Long lastBlockId = workSpace.getBlocks().get(workSpace.getBlocks().size() - 1).getId();

        for (int i = 0; i < WARMUP_REPETITIONS; i++) {
            BlockController.findBlock(lastBlockId, workSpaceId, user);
        }

        long totalNanos = 0L;
        for (int i = 0; i < REPETITIONS; i++) {
            long start = System.nanoTime();
            Block found = BlockController.findBlock(lastBlockId, workSpaceId, user);
            totalNanos += System.nanoTime() - start;
            assertEquals(lastBlockId, found.getId());
        }
        return totalNanos / (double) REPETITIONS;
    }

    private double averageFindCardNanos(int numCards) throws IOException {
        User user = TestDataBuilder.buildUserWithWorkspaces("scale-card@test.com", 1, 1, numCards);
        WorkSpace workSpace = user.getWorkspaces().get(0);
        Long workSpaceId = workSpace.getId();
        Block block = workSpace.getBlocks().get(0);
        Long blockId = block.getId();
        Long lastCardId = block.getCards().get(block.getCards().size() - 1).getId();

        for (int i = 0; i < WARMUP_REPETITIONS; i++) {
            CardController.findCard(lastCardId, blockId, workSpaceId, user);
        }

        long totalNanos = 0L;
        for (int i = 0; i < REPETITIONS; i++) {
            long start = System.nanoTime();
            Card found = CardController.findCard(lastCardId, blockId, workSpaceId, user);
            totalNanos += System.nanoTime() - start;
            assertEquals(lastCardId, found.getId());
        }
        return totalNanos / (double) REPETITIONS;
    }

    // ---------- Utilidad de verificación de crecimiento lineal ----------

    /**
     * Compara el tiempo promedio de búsqueda en una colección pequeña vs.
     * una en el tamaño máximo del ERS. Con una búsqueda lineal, la razón
     * de tiempos debería aproximarse a la razón de tamaños. Se tolera un
     * margen amplio (hasta 6 veces la razón de tamaños esperada) para
     * absorber el ruido propio de mediciones en nanosegundos sobre
     * colecciones pequeñas, sin dejar de descartar una degradación
     * cuadrática o peor.
     */
    private void logAndAssertLinearGrowth(String operationName, int smallSize, int maxSize,
                                           double avgSmallNanos, double avgMaxNanos) {
        double expectedSizeRatio = maxSize / (double) smallSize;
        double observedTimeRatio = avgMaxNanos / Math.max(avgSmallNanos, 1.0);

        System.out.printf("=== CP-U-05 (%s): %d elementos -> %.0f ns | %d elementos -> %.0f ns ===%n",
                operationName, smallSize, avgSmallNanos, maxSize, avgMaxNanos);
        System.out.printf("Razón de tamaños=%.2f, razón de tiempos observada=%.2f%n",
                expectedSizeRatio, observedTimeRatio);

        assertTrue(observedTimeRatio < expectedSizeRatio * 6.0,
                () -> String.format(
                        "%s parece degradarse peor que linealmente: tamaños %d->%d (x%.2f), "
                                + "tiempos %.0fns->%.0fns (x%.2f)",
                        operationName, smallSize, maxSize, expectedSizeRatio,
                        avgSmallNanos, avgMaxNanos, observedTimeRatio));
    }
}
