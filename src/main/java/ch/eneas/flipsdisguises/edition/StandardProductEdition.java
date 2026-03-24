package ch.eneas.flipsdisguises.edition;

import ch.eneas.flips.loader.api.Injectable;

@Injectable
public final class StandardProductEdition implements ProductEdition {

    @Override
    public boolean disguiseCommandsEnabled() {
        return true;
    }

    @Override
    public boolean programmaticDisguiseApiEnabled() {
        return true;
    }
}
