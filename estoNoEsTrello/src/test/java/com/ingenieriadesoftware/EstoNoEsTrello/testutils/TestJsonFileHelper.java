package com.ingenieriadesoftware.EstoNoEsTrello.testutils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.ingenieriadesoftware.EstoNoEsTrello.model.User;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * UserJsonController lee/escribe siempre en la ruta fija de classpath
 * "JSONs/Users.json" (ClassPathResource). En el classpath de pruebas de
 * Maven, ese recurso se resuelve a target/test-classes/JSONs/Users.json,
 * que es una copia independiente del de target/classes usado en producción.
 * <p>
 * Esta utilidad permite:
 * 1) localizar el archivo físico que UserJsonController va a usar durante
 *    los tests (resolveUsersJsonPath), y
 * 2) sobrescribirlo con datos sintéticos de un tamaño determinado
 *    (writeUsers), sin necesidad de refactorizar la clase bajo prueba.
 * <p>
 * NOTA: aunque los usuarios sintéticos generados aquí llevan la lista
 * "workspaces" vacía, Gson igual construye reflexivamente el árbol
 * completo de TypeAdapters declarado por User (User -> List&lt;WorkSpace&gt;
 * -> List&lt;Block&gt; -> List&lt;Card&gt; -> LocalDate), sin importar si esas
 * listas están vacías en tiempo de ejecución. Desde Java 17+, el módulo
 * java.base no permite el acceso reflexivo a los campos internos de
 * java.time.LocalDate, por lo que se registra aquí un serializador propio
 * para LocalDate (equivalente al LocalDateAdapter, package-private, que
 * usa UserJsonController) y así evitar que Gson intente esa reflexión.
 */
public final class TestJsonFileHelper {

    private static final String USERS_JSON_CLASSPATH = "JSONs/Users.json";

    private TestJsonFileHelper() {
    }

    public static Path resolveUsersJsonPath() {
        try {
            ClassPathResource resource = new ClassPathResource(USERS_JSON_CLASSPATH);
            return resource.getFile().toPath();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo localizar " + USERS_JSON_CLASSPATH + " en el classpath de pruebas. "
                            + "Verifique que exista en src/test/resources/JSONs/Users.json o que "
                            + "src/main/resources/JSONs/Users.json se copie al classpath de test.", e);
        }
    }

    public static String backup(Path usersJsonPath) throws IOException {
        return Files.readString(usersJsonPath, StandardCharsets.UTF_8);
    }

    public static void restore(Path usersJsonPath, String originalContent) throws IOException {
        Files.writeString(usersJsonPath, originalContent, StandardCharsets.UTF_8);
    }

    public static void writeUsers(Path usersJsonPath, List<User> users) throws IOException {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(LocalDate.class,
                        (JsonSerializer<LocalDate>) (date, type, context) ->
                                new JsonPrimitive(date.toString()))
                .create();
        Files.writeString(usersJsonPath, gson.toJson(users), StandardCharsets.UTF_8);
    }
}
