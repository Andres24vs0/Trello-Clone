package com.ingenieriadesoftware.EstoNoEsTrello.JsonControllers;

import com.ingenieriadesoftware.EstoNoEsTrello.model.User;
import com.ingenieriadesoftware.EstoNoEsTrello.testutils.TestDataBuilder;
import com.ingenieriadesoftware.EstoNoEsTrello.testutils.TestJsonFileHelper;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-U-01 — UserJsonController.findTotalUsers()
 * Plan de Pruebas ST-KANBAN-PERF-PLAN, sección 5.1 / 6.1.
 * <p>
 * Entrada: Users.json con 0, 10, 100 y 1.000 usuarios serializados.
 * Resultado esperado: el tiempo de deserialización crece linealmente
 * respecto al tamaño del archivo (no cuadrática ni exponencialmente).
 * <p>
 * Pasos (según el plan):
 *  1) Generar los 4 archivos.
 *  2) Invocar findTotalUsers() sobre cada uno, midiendo con System.nanoTime().
 *  3) Repetir 30 veces por archivo y promediar.
 *  4) Tabular tiempo vs. tamaño.
 * <p>
 * NOTA TÉCNICA: UserJsonController usa una ruta de classpath fija
 * ("JSONs/Users.json"), por lo que no se puede inyectar un archivo distinto
 * por test. En su lugar, esta prueba sobrescribe el propio
 * target/test-classes/JSONs/Users.json antes de cada medición y RESTAURA
 * su contenido original al finalizar (@AfterAll), para no dejar el
 * repositorio de pruebas en un estado distinto al original.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserJsonControllerPerformanceTest {

    private static final int[] VOLUMES = {0, 10, 100, 1_000};
    private static final int REPETITIONS = 30;
    private static final int WARMUP_REPETITIONS = 5;

    private static Path usersJsonPath;
    private static String originalContent;

    @BeforeAll
    static void locateAndBackupUsersJson() throws IOException {
        usersJsonPath = TestJsonFileHelper.resolveUsersJsonPath();
        originalContent = TestJsonFileHelper.backup(usersJsonPath);
    }

    @AfterAll
    static void restoreUsersJson() throws IOException {
        TestJsonFileHelper.restore(usersJsonPath, originalContent);
    }

    @Test
    @Order(1)
    @DisplayName("CP-U-01: el tiempo de findTotalUsers() crece linealmente con 0/10/100/1000 usuarios")
    void deserializationTimeShouldGrowLinearlyWithFileSize() throws IOException {
        Map<Integer, Double> avgNanosByVolume = new LinkedHashMap<>();

        for (int volume : VOLUMES) {
            List<User> synthetic = TestDataBuilder.buildSimpleUsers(volume);
            TestJsonFileHelper.writeUsers(usersJsonPath, synthetic);

            // Warmup: reduce el ruido de JIT/compilación antes de medir
            for (int i = 0; i < WARMUP_REPETITIONS; i++) {
                UserJsonController.findTotalUsers();
            }

            long totalNanos = 0L;
            for (int i = 0; i < REPETITIONS; i++) {
                long start = System.nanoTime();
                var result = UserJsonController.findTotalUsers();
                totalNanos += System.nanoTime() - start;
                assertEquals(volume, result.size(),
                        "findTotalUsers() no devolvió la cantidad de usuarios esperada");
            }

            double avgNanos = totalNanos / (double) REPETITIONS;
            avgNanosByVolume.put(volume, avgNanos);
        }

        // 4) Tabular tiempo vs. tamaño (queda en el log de la ejecución / reporte de Surefire)
        System.out.println("=== CP-U-01: tiempo promedio de findTotalUsers() por volumen ===");
        avgNanosByVolume.forEach((volume, avgNanos) ->
                System.out.printf("%,6d usuarios -> %10.3f ms (promedio de %d repeticiones)%n",
                        volume, avgNanos / 1_000_000.0, REPETITIONS));

        // Verificación de crecimiento sub-cuadrático:
        // Si el tiempo fuera realmente lineal, t(1000) / t(100) ~ 10.
        // Si fuera cuadrático, esa razón sería ~100. Se tolera un margen
        // amplio (hasta 40x) para absorber ruido de medición en JVMs
        // compartidas, pero se sigue descartando con margen un
        // comportamiento cuadrático real.
        double t100 = Math.max(avgNanosByVolume.get(100), 1.0); // evita división por 0
        double t1000 = avgNanosByVolume.get(1_000);
        double ratio = t1000 / t100;

        System.out.printf("Razón t(1000)/t(100) = %.2f (lineal ~10, cuadrático ~100)%n", ratio);
        assertTrue(ratio < 40.0,
                () -> String.format(
                        "El tiempo de findTotalUsers() parece crecer peor que linealmente: "
                                + "t(100)=%.3fms, t(1000)=%.3fms, razón=%.2f",
                        t100 / 1_000_000.0, t1000 / 1_000_000.0, ratio));
    }
}
