package ch.eneas.flipsdisguises.disguise.port;

import ch.eneas.flipsdisguises.disguise.domain.SkinSnapshot;

import java.util.concurrent.CompletableFuture;

public interface SkinCatalog {

    CompletableFuture<SkinSnapshot> resolveUsername(String username);
}
