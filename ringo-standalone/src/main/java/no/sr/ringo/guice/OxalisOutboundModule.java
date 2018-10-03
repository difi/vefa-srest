package no.sr.ringo.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import no.difi.oxalis.outbound.OxalisOutboundComponent;

public class OxalisOutboundModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(OxalisOutboundComponent.class).in(Singleton.class);
    }
}
