package com.ingenieriadesoftware.EstoNoEsTrello.testutils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.ingenieriadesoftware.EstoNoEsTrello.model.User;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Script generador de datos sintéticos (Gson) mencionado en el Plan de
 * Pruebas ST-KANBAN-PERF-PLAN, sección 7 ("Gson - script de datos
 * sintéticos") y en la precondición de CP-I-03 / CP-S-04 ("no existe aún
 * en el proyecto; debe crearse como utilidad de test").
 * <p>
 * Sobrescribe Users.json con uno de dos escenarios:
 * <ul>
 *   <li><b>nominal</b>: 100 usuarios (usuario1@carga.test ... usuario100@carga.test),
 *   cada uno con 5 Workspaces vacíos (sin Blocks/Cards). Cubre el volumen
 *   nominal de CP-S-01 (10 usuarios x 5 espacios) y soporta hasta 100
 *   hilos virtuales concurrentes de CP-S-02, cada uno operando sobre un
 *   usuario distinto (variable JMeter __threadNum mapea 1:1 a usuarioN@carga.test).</li>
 *   <li><b>max</b>: el escenario "nominal" completo, más un usuario adicional
 *   ("usuariomax@carga.test") sembrado en el volumen máximo declarado por
 *   el ERS: 15 Workspaces x 50 Blocks x 50 Cards = 37.500 tarjetas. Este es
 *   el dataset requerido por CP-I-03 y CP-S-04.</li>
 * </ul>
 * <p>
 * Uso:
 * <pre>
 *   mvn -q test-compile exec:java \
 *     -Dexec.mainClass=com.ingenieriadesoftware.EstoNoEsTrello.testutils.PerformanceDataSeeder \
 *     -Dexec.classpathScope=test \
 *     -Dexec.args="nominal"
 *
 *   mvn -q test-compile exec:java \
 *     -Dexec.mainClass=com.ingenieriadesoftware.EstoNoEsTrello.testutils.PerformanceDataSeeder \
 *     -Dexec.classpathScope=test \
 *     -Dexec.args="max"
 * </pre>
 * El primer argumento es el escenario ("nominal" o "max"). El segundo
 * argumento (opcional) es la ruta de salida; por defecto
 * src/main/resources/JSONs/Users.json (la ruta que UserJsonController lee
 * en tiempo de ejecución vía ClassPathResource).
 * <p>
 * IMPORTANTE: ejecutar este script con el servidor DETENIDO y volver a
 * levantarlo (mvn spring-boot:run) después de sembrar, para que Maven
 * copie el nuevo Users.json a target/classes antes de que Tomcat lo lea.
 */
public final class PerformanceDataSeeder {

    private static final String DEFAULT_OUTPUT = "src/main/resources/JSONs/Users.json";
    private static final int NOMINAL_USER_COUNT = 100;
    private static final int NOMINAL_WORKSPACES_PER_USER = 5;
    private static final String MAX_USER_EMAIL = "usuariomax@carga.test";
    private static final int MAX_WORKSPACES = 15;
    private static final int MAX_BLOCKS_PER_WORKSPACE = 50;
    private static final int MAX_CARDS_PER_BLOCK = 50;

    private PerformanceDataSeeder() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Uso: PerformanceDataSeeder <nominal|max> [rutaSalida]");
            System.exit(1);
        }
        String scenario = args[0].trim().toLowerCase();
        Path output = Paths.get(args.length >= 2 ? args[1] : DEFAULT_OUTPUT);

        List<User> users = new ArrayList<>();
        for (int i = 1; i <= NOMINAL_USER_COUNT; i++) {
            String email = "usuario" + i + "@carga.test";
            users.add(TestDataBuilder.buildUserWithWorkspaces(
                    email, NOMINAL_WORKSPACES_PER_USER, 0, 0));
        }

        if (scenario.equals("max")) {
            users.add(TestDataBuilder.buildUserWithWorkspaces(
                    MAX_USER_EMAIL, MAX_WORKSPACES, MAX_BLOCKS_PER_WORKSPACE, MAX_CARDS_PER_BLOCK));
        } else if (!scenario.equals("nominal")) {
            System.out.println("Escenario desconocido: " + scenario + " (use 'nominal' o 'max')");
            System.exit(1);
        }

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
                .create();

        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        Files.writeString(output, gson.toJson(users), StandardCharsets.UTF_8);

        long totalCards = users.stream()
                .flatMap(u -> u.getWorkspaces().stream())
                .flatMap(w -> w.getBlocks().stream())
                .mapToLong(b -> b.getCards().size())
                .sum();

        System.out.println("Escenario '" + scenario + "' escrito en " + output.toAbsolutePath());
        System.out.println("Usuarios: " + users.size() + " | Tarjetas totales en el archivo: " + totalCards);
    }

    /** Equivalente al adaptador interno (package-private) de UserJsonController. */
    private static class LocalDateAdapter implements JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {
        @Override
        public JsonElement serialize(LocalDate date, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(date.format(DateTimeFormatter.ISO_LOCAL_DATE));
        }

        @Override
        public LocalDate deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return LocalDate.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE);
        }
    }
}
