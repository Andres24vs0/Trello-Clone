package com.ingenieriadesoftware.EstoNoEsTrello.Acceptance;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * CP-A-03 — Validación de la capacidad máxima declarada por el ERS, en
 * sesión conjunta con el cliente o su representante (UAT).
 * <p>
 * Plan de Pruebas ST-KANBAN-PERF-PLAN, secciones 5.4 / 6.4, y
 * Especificación de Pruebas, tabla CP-A-03.
 * <p>
 * Este caso es, por definición, una validación HUMANA: el Líder General
 * presenta el escenario al cliente y recoge su firma en el Acta de
 * Aceptación (ver documento "Acta_Aceptacion_Pruebas_Desempeno.docx").
 * Esta clase no reemplaza esa validación; es el script de apoyo que se
 * proyecta durante la sesión en vivo para que el cliente vea, con sus
 * propios ojos, el tiempo de cada operación CRUD contra el dataset de
 * capacidad máxima (15 Workspaces x 50 Blocks x 50 Cards = 37.500
 * tarjetas), reutilizando:
 * <p>
 * 1) El mismo sembrador de datos sintéticos ya requerido por CP-I-03/CP-S-04
 *    (testutils.PerformanceDataSeeder, escenario "max"), y
 * 2) Los mismos umbrales de CP-S-01/CP-S-02 (2000 ms por operación,
 *    200 ms de latencia deseable), ahora observados en vivo.
 * <p>
 * USO recomendado durante la sesión con el cliente:
 * <pre>
 *   1) Sembrar el dataset maximo (una sola vez, servidor detenido):
 *      mvn -q test-compile exec:java \
 *        -Dexec.mainClass=com.ingenieriadesoftware.EstoNoEsTrello.testutils.PerformanceDataSeeder \
 *        -Dexec.classpathScope=test -Dexec.args="max"
 *   2) Levantar el servidor:
 *      mvn spring-boot:run
 *   3) Proyectar esta clase en vivo frente al cliente:
 *      mvn -q test-compile exec:java \
 *        -Dexec.mainClass=com.ingenieriadesoftware.EstoNoEsTrello.Acceptance.CP_A03_MaxCapacityUatDemoRunner \
 *        -Dexec.classpathScope=test -Dexec.args="http://localhost:8080"
 *   4) Con la salida en pantalla, completar y firmar el Acta de Aceptación
 *      junto con el cliente.
 * </pre>
 */
public final class CP_A03_MaxCapacityUatDemoRunner {

    // Usuario sembrado por PerformanceDataSeeder en el escenario "max"
    private static final String MAX_USER_EMAIL = "usuariomax@carga.test";
    private static final long UMBRAL_OPERACION_MS = 2000L;
    private static final long UMBRAL_LATENCIA_DESEABLE_MS = 200L;

    public static void main(String[] args) throws Exception {
        String baseUrl = args.length >= 1 ? args[0] : "http://localhost:8080";

        AcceptanceEvidenceWriter evidence = new AcceptanceEvidenceWriter("CP-A-03-uat-cliente.txt");
        evidence.header("CP-A-03 - Validacion conjunta con el cliente en capacidad maxima (UAT)");
        evidence.line("Servidor objetivo: " + baseUrl);
        evidence.line("Usuario de capacidad maxima: " + MAX_USER_EMAIL
                + " (15 WorkSpaces x 50 Blocks x 50 Cards = 37.500 tarjetas)");
        evidence.line("");
        evidence.line(">>> Presente esta pantalla al cliente antes de continuar <<<");
        evidence.line("");

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        boolean todoDentroDeUmbral = true;

        todoDentroDeUmbral &= ejecutarYRegistrar(evidence, client,
                "Consultar los 15 espacios de trabajo (loadWorkSpaces)",
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/user/loadWorkSpaces?email=" + MAX_USER_EMAIL))
                        .GET().timeout(Duration.ofSeconds(15)).build());

        evidence.line("");
        evidence.line("Nota: para consultar Blocks/Cards de un WorkSpace especifico durante la");
        evidence.line("sesion en vivo, tome el 'id' que el cliente elija de la respuesta anterior y ejecute:");
        evidence.line("  GET " + baseUrl + "/user/loadBlocks?email=" + MAX_USER_EMAIL + "&workspaceid=<ID_ELEGIDO>");
        evidence.line("desde el navegador o Postman, cronometrando junto al cliente.");
        evidence.line("");

        evidence.header("Resultado preliminar (para discutir con el cliente)");
        evidence.line("Todas las operaciones ejecutadas por este script quedaron dentro del umbral "
                + "de " + UMBRAL_OPERACION_MS + " ms: " + (todoDentroDeUmbral ? "SI" : "NO"));
        evidence.line("Este resultado es INSUMO para el Acta de Aceptacion, no un reemplazo de la "
                + "validacion y firma del cliente (criterio de aceptacion CP-A-03).");
        evidence.line("Evidencia completa en: " + evidence.getReportFile().toAbsolutePath());
    }

    private static boolean ejecutarYRegistrar(AcceptanceEvidenceWriter evidence, HttpClient client,
                                               String descripcion, HttpRequest request) throws Exception {
        long start = System.nanoTime();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        boolean dentroDeUmbral = elapsedMs <= UMBRAL_OPERACION_MS;
        String calificacionLatencia = elapsedMs <= UMBRAL_LATENCIA_DESEABLE_MS ? "optima" : "aceptable-pero-alta";

        evidence.line(descripcion);
        evidence.line(String.format("  -> HTTP %d en %d ms (umbral %d ms, latencia %s) -> %s",
                response.statusCode(), elapsedMs, UMBRAL_OPERACION_MS, calificacionLatencia,
                dentroDeUmbral ? "ACEPTABLE" : "RECHAZADO"));
        return dentroDeUmbral;
    }
}
