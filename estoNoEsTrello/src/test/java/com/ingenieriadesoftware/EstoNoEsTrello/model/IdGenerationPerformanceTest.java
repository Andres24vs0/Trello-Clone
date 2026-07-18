package com.ingenieriadesoftware.EstoNoEsTrello.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-U-04 — Block.generate12DigitId() / Card.generate12DigitId() /
 * WorkSpace.generate12DigitId()
 * Plan de Pruebas ST-KANBAN-PERF-PLAN, sección 5.1 / 6.1.
 * <p>
 * Entrada: 10.000 invocaciones consecutivas por clase.
 * Resultado esperado: el tiempo total no supera 500 ms por cada 10.000
 * IDs generados y no se producen colisiones de identificador.
 * <p>
 * NOTA TÉCNICA: en las 3 clases, generate12DigitId() es un método
 * "private static", usado solo para inicializar el contador interno
 * idCounter. No existe una API pública para invocarlo repetidamente,
 * por lo que esta prueba usa reflexión (Method.setAccessible(true)) para
 * llamarlo directamente, sin modificar el código de producción. El
 * método no tiene efectos colaterales (no incrementa idCounter), por lo
 * que invocarlo miles de veces desde el test es seguro.
 */
class IdGenerationPerformanceTest {

    private static final int INVOCATIONS = 10_000;
    private static final long MAX_TOTAL_MILLIS = 500L;

    @Test
    @DisplayName("CP-U-04: Block.generate12DigitId() — 10.000 invocaciones < 500 ms, sin colisiones")
    void block_generate12DigitId_meetsPerformanceAndUniquenessBudget() throws Exception {
        assertIdGenerationPerformance(Block.class);
    }

    @Test
    @DisplayName("CP-U-04: Card.generate12DigitId() — 10.000 invocaciones < 500 ms, sin colisiones")
    void card_generate12DigitId_meetsPerformanceAndUniquenessBudget() throws Exception {
        assertIdGenerationPerformance(Card.class);
    }

    @Test
    @DisplayName("CP-U-04: WorkSpace.generate12DigitId() — 10.000 invocaciones < 500 ms, sin colisiones")
    void workSpace_generate12DigitId_meetsPerformanceAndUniquenessBudget() throws Exception {
        assertIdGenerationPerformance(WorkSpace.class);
    }

    private void assertIdGenerationPerformance(Class<?> targetClass) throws Exception {
        Method generateIdMethod = targetClass.getDeclaredMethod("generate12DigitId");
        generateIdMethod.setAccessible(true);

        Set<Long> generatedIds = new HashSet<>(INVOCATIONS);

        long startNanos = System.nanoTime();
        for (int i = 0; i < INVOCATIONS; i++) {
            long generatedId = (long) generateIdMethod.invoke(null);
            generatedIds.add(generatedId);
        }
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;

        System.out.printf("=== CP-U-04 (%s): %d invocaciones en %d ms, %d IDs únicos ===%n",
                targetClass.getSimpleName(), INVOCATIONS, elapsedMillis, generatedIds.size());

        assertTrue(elapsedMillis <= MAX_TOTAL_MILLIS,
                () -> String.format("%s.generate12DigitId(): %d invocaciones tardaron %d ms, "
                                + "supera el umbral de %d ms",
                        targetClass.getSimpleName(), INVOCATIONS, elapsedMillis, MAX_TOTAL_MILLIS));

        assertEquals(INVOCATIONS, generatedIds.size(),
                () -> String.format("%s.generate12DigitId(): se detectaron colisiones de ID "
                                + "(%d únicos de %d generados)",
                        targetClass.getSimpleName(), generatedIds.size(), INVOCATIONS));
    }
}
