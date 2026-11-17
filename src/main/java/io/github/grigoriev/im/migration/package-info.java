/**
 * The migration of the database: {@link io.github.grigoriev.im.migration.Migration} runs Flyway as the owner of the
 * tables in a step of its own, {@link io.github.grigoriev.im.migration.SchemaCheck} lets the app start only on the
 * current schema.
 */
package io.github.grigoriev.im.migration;
