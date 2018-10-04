package no.sr.ringo.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import no.sr.ringo.email.EmailService;
import no.sr.ringo.email.NoEmailServiceImpl;
import no.sr.ringo.message.PeppolMessageRepository;
import no.sr.ringo.message.PeppolMessageRepositoryImpl;
import no.sr.ringo.oxalis.OxalisDocumentSender;
import no.sr.ringo.oxalis.PeppolDocumentSender;

/**
 * Bindings for our service objects as used in RingoStandalone.
 * <p>
 * <p>
 * e.g.
 * Repositories
 * Stateless UseCases
 * Services
 * Email services etc                                                                                                                                              ©
 * <p>
 * Configured for production or test through the constructor.
 * </p>
 * <p>
 * <p>TODO: This class should be refactored as it violates Google Guice best practice by introducing conditional logic.
 * This could be fixed by creating an abstract Ringo service module with two decendant classes; one for production and one for non-production.
 * </p>
 *
 * @author andy
 * @author thore
 */
public class RingoServiceModule extends AbstractModule {

    /**
     * Configures a {@link com.google.inject.Binder} via the exposed methods.
     */
    @Override
    protected void configure() {
        bindRepositories();
        bindPeppolDocumentSender();
        bindEmailService();
    }

    private void bindRepositories() {
        bind(PeppolMessageRepository.class).to(PeppolMessageRepositoryImpl.class).in(Singleton.class);
    }

    private void bindPeppolDocumentSender() {
        bind(PeppolDocumentSender.class).to(OxalisDocumentSender.class);
    }

    private void bindEmailService() {
        bind(EmailService.class).to(NoEmailServiceImpl.class).in(Singleton.class);
    }

}