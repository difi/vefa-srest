package no.sr.ringo.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import eu.peppol.persistence.api.account.AccountRepository;
import eu.peppol.persistence.jdbc.AccountRepositoryImpl;
import no.sr.ringo.common.IsProductionServer;
import no.sr.ringo.email.*;
import no.sr.ringo.message.PeppolMessageRepository;
import no.sr.ringo.message.PeppolMessageRepositoryImpl;
import no.sr.ringo.oxalis.DummySender;
import no.sr.ringo.oxalis.OxalisDocumentSender;
import no.sr.ringo.oxalis.PeppolDocumentSender;
import no.sr.ringo.queue.QueueRepository;
import no.sr.ringo.queue.QueueRepositoryImpl;

/**
 * Bindings for our service objects as used in RingoStandalone.
 *
 *
 * e.g.
 * Repositories
 * Stateless UseCases
 * Services
 * Email services etc                                                                                                                                              ©
 * <p>
 * Configured for production or test through the constructor.
 * </p>
 *
 * <p>TODO: This class should be refactored as it violates Google Guice best practice by introducing conditional logic.
 * This could be fixed by creating an abstract Ringo service module with two decendant classes; one for production and one for non-production.
 * </p>
 *
 * @author andy
 * @author thore
 */
public class RingoServiceModule extends AbstractModule {

    private boolean production;

    public RingoServiceModule(boolean production) {
        this.production = production;
    }

    /**
     * Configures a {@link com.google.inject.Binder} via the exposed methods.
     */
    @Override
    protected void configure() {
        bind(IsProductionServer.class).toInstance(new IsProductionServer(production));
        bindRepositories();
        bindPeppolDocumentSender();
        bindEmailService();
    }

    private void bindRepositories() {
        bind(PeppolMessageRepository.class).to(PeppolMessageRepositoryImpl.class).in(Singleton.class);
        bind(QueueRepository.class).to(QueueRepositoryImpl.class).in(Singleton.class);
    }

    private void bindPeppolDocumentSender() {
        if (production) {
            bind(PeppolDocumentSender.class).to(OxalisDocumentSender.class);
        } else {
            bind(PeppolDocumentSender.class).to(DummySender.class);
        }
    }

    private void bindEmailService() {
        bind(EmailService.class).to(NoEmailServiceImpl.class).in(Singleton.class);
    }

}