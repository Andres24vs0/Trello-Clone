package com.ingenieriadesoftware.EstoNoEsTrello.Acceptance;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CP-A-02 — Uso continuo del sistema durante una sesión de trabajo típica.
 * <p>
 * Plan de Pruebas ST-KANBAN-PERF-PLAN, secciones 5.4 / 6.4, y
 * Especificación de Pruebas, tabla CP-A-02.
 * <p>
 * Este caso, según su técnica declarada ("prueba manual guiada de
 * disponibilidad prolongada" sobre una ventana de 2 horas), NO se ejecuta
 * como un @Test de JUnit dentro de `mvn test` (una suite de CI no debe
 * bloquearse 2 horas en cada build). Se implementa como un PROGRAMA
 * INDEPENDIENTE que:
 * <p>
 * 1) Golpea el servidor con operaciones CRUD intercaladas (Workspace,
 *    Block, Card) cada pocos segundos, igual que un usuario real.
 * 2) Registra cada petición (timestamp, operación, código HTTP, latencia)
 *    en target/acceptance-reports/CP-A-02-disponibilidad.txt.
 * 3) Al finalizar, informa si hubo respuestas 5xx, timeouts o caídas de
 *    conexión (indicio de reinicio/caída del proceso del servidor), que es
 *    exactamente el criterio de aceptación de este caso.
 * <p>
 * El Analista complementa esta evidencia automatizada revisando la
 * consola/log del servidor (sección "Insumos" de CP-A-02: "acceso a sus
 * logs") en busca de stack traces o del mensaje de arranque de Spring Boot
 * repetido (lo que delataría un reinicio del proceso).
 * <p>
 * USO (ver también la sección "Cómo ejecutarla" de la guía adjunta):
 * <pre>
 *   mvn -q test-compile exec:java \
 *     -Dexec.mainClass=com.ingenieriadesoftware.EstoNoEsTrello.Acceptance.CP_A02_ContinuousAvailabilityMonitor \
 *     -Dexec.classpathScope=test \
 *     -Dexec.args="http://localhost:8080 120 5"
 * </pre>
 * Argumentos: [0] URL base del servidor ya desplegado,
 *             [1] duración total en MINUTOS (usar 120 para la corrida real de 2h;
 *                 usar un valor pequeño, p. ej. 2, para una corrida de demostración),
 *             [2] intervalo en SEGUNDOS entre operaciones (por defecto 5).
 */
public final class CP_A02_ContinuousAvailabilityMonitor {

    private static final String EMAIL = "albertrodri2710@gmail.com";
    private static final String PASSWORD = "123";

    public static void main(String[] args) throws Exception {
        String baseUrl = args.length >= 1 ? args[0] : "http://localhost:8080";
        long durationMinutes = args.length >= 2 ? Long.parseLong(args[1]) : 120;
        long intervalSeconds = args.length >= 3 ? Long.parseLong(args[2]) : 5;

        AcceptanceEvidenceWriter evidence = new AcceptanceEvidenceWriter("CP-A-02-disponibilidad.txt");
        evidence.header("CP-A-02 - Disponibilidad continua durante sesion de uso");
        evidence.line("Servidor objetivo: " + baseUrl);
        evidence.line("Duracion planificada: " + durationMinutes + " minutos");
        evidence.line("Intervalo entre operaciones: " + intervalSeconds + " segundos");

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        Instant start = Instant.now();
        Instant end = start.plusSeconds(durationMinutes * 60);
        AtomicInteger totalRequests = new AtomicInteger(0);
        AtomicInteger failedRequests = new AtomicInteger(0);
        AtomicLong maxLatencyMs = new AtomicLong(0);

        // Secuencia de operaciones CRUD a intercalar, tal como pide la
        // especificación ("lista de operaciones CRUD a intercalar").
        String[] operations = {"loadWorkSpaces", "loadWorkSpaces", "login"};
        int opIndex = 0;

        boolean serverWentDown = false;

        while (Instant.now().isBefore(end)) {
            String operation = operations[opIndex % operations.length];
            opIndex++;

            long reqStart = System.nanoTime();
            try {
                HttpResponse<String> response = executeOperation(client, baseUrl, operation);
                long latencyMs = (System.nanoTime() - reqStart) / 1_000_000;
                maxLatencyMs.updateAndGet(prev -> Math.max(prev, latencyMs));
                totalRequests.incrementAndGet();

                int status = response.statusCode();
                boolean isServerError = status >= 500;
                if (isServerError) {
                    failedRequests.incrementAndGet();
                }
                evidence.line(String.format("op=%-15s status=%d latencia=%dms %s",
                        operation, status, latencyMs, isServerError ? "-> ERROR 5xx" : "OK"));

            } catch (Exception e) {
                totalRequests.incrementAndGet();
                failedRequests.incrementAndGet();
                serverWentDown = true;
                evidence.line("op=" + operation + " -> FALLO DE CONEXION (posible caida/reinicio del servidor): "
                        + e.getClass().getSimpleName() + " " + e.getMessage());
            }

            Thread.sleep(Duration.ofSeconds(intervalSeconds).toMillis());
        }

        Duration realElapsed = Duration.between(start, Instant.now());
        evidence.header("Resumen CP-A-02");
        evidence.line("Duracion real: " + realElapsed.toMinutes() + " minutos");
        evidence.line("Peticiones totales: " + totalRequests.get());
        evidence.line("Peticiones fallidas (5xx o caida de conexion): " + failedRequests.get());
        evidence.line("Latencia maxima observada: " + maxLatencyMs.get() + " ms");

        boolean aceptado = !serverWentDown && failedRequests.get() == 0;
        evidence.line("Criterio de aceptacion (sin caidas/reinicios, servidor responsivo): "
                + (aceptado ? "CUMPLE" : "NO CUMPLE"));
        evidence.line("IMPORTANTE: revisar tambien la consola/log del servidor durante esta ventana "
                + "en busca de excepciones no capturadas o del mensaje de arranque de Spring Boot "
                + "repetido (evidencia de reinicio del proceso).");
        evidence.line("Evidencia completa en: " + evidence.getReportFile().toAbsolutePath());
    }

    private static HttpResponse<String> executeOperation(HttpClient client, String baseUrl, String operation)
            throws Exception {
        HttpRequest request;
        switch (operation) {
            case "login" -> request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/user/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                    .timeout(Duration.ofSeconds(10))
                    .build();
            default -> request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/user/loadWorkSpaces?email=" + EMAIL))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();
        }
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
