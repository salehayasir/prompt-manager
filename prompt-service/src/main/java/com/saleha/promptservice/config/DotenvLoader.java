package com.saleha.promptservice.config;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads key=value pairs from a .env file in the current working directory
 * into System properties, before the Spring context starts. This makes
 * ${VAR} placeholders in application.yaml resolve from .env in local
 * development, without requiring any external dependency.
 *
 * Real environment variables (System.getenv) always take precedence over
 * .env, and .env never overrides a value that's already set.
 */
public final class DotenvLoader {

    private DotenvLoader() {
    }

    public static void load() {
        load(".env");
    }

    public static void load(String fileName) {

        Path path = Path.of(fileName);

        if (!Files.exists(path)) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {

            String line;

            while ((line = reader.readLine()) != null) {

                line = line.trim();

                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int separatorIndex = line.indexOf('=');

                if (separatorIndex <= 0) {
                    continue;
                }

                String key = line.substring(0, separatorIndex).trim();
                String value = line.substring(separatorIndex + 1).trim();

                value = stripSurroundingQuotes(value);

                // Real OS env vars win; don't clobber anything already set.
                if (System.getenv(key) == null && System.getProperty(key) == null) {
                    System.setProperty(key, value);
                }
            }

        } catch (IOException e) {

            System.err.println("Warning: failed to read " + fileName + ": " + e.getMessage());
        }
    }

    private static String stripSurroundingQuotes(String value) {

        if (value.length() >= 2) {

            boolean doubleQuoted = value.startsWith("\"") && value.endsWith("\"");
            boolean singleQuoted = value.startsWith("'") && value.endsWith("'");

            if (doubleQuoted || singleQuoted) {
                return value.substring(1, value.length() - 1);
            }
        }

        return value;
    }
}
