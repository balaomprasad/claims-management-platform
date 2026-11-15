package com.claimsphere.document.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageTest {

    @TempDir
    Path root;

    @Test
    void storesLoadsAndDeletesWithinRoot() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(root.toString());
        storage.store("claims/c1/doc", new ByteArrayInputStream(new byte[]{1, 2, 3}));

        assertThat(storage.load("claims/c1/doc").getContentAsByteArray()).containsExactly(1, 2, 3);
        storage.delete("claims/c1/doc");
        assertThat(root.resolve("claims/c1/doc")).doesNotExist();
    }

    @Test
    void rejectsKeysThatEscapeTheRoot() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(root.toString());

        assertThatThrownBy(() -> storage.resolve("../outside")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.resolve("claims/../../outside")).isInstanceOf(IllegalArgumentException.class);
    }
}
