/* Created by steinar on 01.01.12 at 17:54 */
package no.sr.ringo.account;

import com.google.inject.Inject;
import com.google.inject.servlet.RequestScoped;
import no.difi.vefa.peppol.common.model.ParticipantIdentifier;
import no.sr.ringo.RingoConstant;
import no.sr.ringo.common.MessageHelper;
import no.sr.ringo.email.EmailService;
import no.sr.ringo.persistence.guice.jdbc.JdbcTxManager;
import no.sr.ringo.persistence.guice.jdbc.Transactional;
import no.sr.ringo.security.CredentialHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UseCase responsible for the process of registration of new users
 *
 * @author Adam Mscisz adam@sendregning.no
 */
@RequestScoped
public class RegisterUseCase {

    private static Logger logger = LoggerFactory.getLogger(RegisterUseCase.class);

    private AccountRepository accountRepository;
    private JdbcTxManager jdbcTxManager;
    private final EmailService emailService;
    private final CredentialHandler credentialHandler;

    @Inject
    RegisterUseCase(AccountRepository accountRepository, JdbcTxManager jdbcTxManager, EmailService emailService, CredentialHandler credentialHandler) {
        this.jdbcTxManager = jdbcTxManager;
        this.accountRepository = accountRepository;
        this.emailService = emailService;
        this.credentialHandler = credentialHandler;
    }

    public ValidationResult validateData(RegistrationData registrationData) {

        boolean valid = true;
        String message = null;

        if (isEmpty(registrationData.getName())) {
            message = MessageHelper.getMessage("reg.name.required");
        } else if (isEmpty(registrationData.getPassword())) {
            message = MessageHelper.getMessage("reg.password.required");
        } else if (isEmpty(registrationData.getOrgNo())) {
            message = MessageHelper.getMessage("reg.orgNoRequired");
        } else if (isEmpty(registrationData.getUsername())) {
            message = MessageHelper.getMessage("reg.username.required");
        } else {

            // TODO: validate the organisation number?
            if (registrationData.getOrgNo() == null || registrationData.getOrgNo().trim().length() == 0) {
                message = MessageHelper.getMessage("reg.orgNo.invalid", registrationData.getOrgNo());
            }
        }

        if (message != null) {
            valid = false;
        }

        return new ValidationResult(valid, message);

    }

    @Transactional
    public RegistrationProcessResult registerUser(final RegistrationData registrationData) {

        // check if user with this username already exists
        boolean exists = accountRepository.accountExists(new UserName(registrationData.getUsername()));
        if (exists) {
            return new RegistrationProcessResult(RegistrationProcessResult.RegistrationSource.RINGO, false, MessageHelper.getMessage("reg.user.exists"));
        }

        // Prefix given orgNo with 9908
        final ParticipantIdentifier peppolParticipantId = ParticipantIdentifier.of(registrationData.getOrgNo());
        if (peppolParticipantId == null) {
            throw new IllegalArgumentException("Provided organisation number is invalid");
        }

        Account orgNoUsed = accountRepository.findAccountByParticipantIdentifier(peppolParticipantId);
        if (orgNoUsed != null) {
            return new RegistrationProcessResult(RegistrationProcessResult.RegistrationSource.RINGO, false, MessageHelper.getMessage("reg.orgNo.registered"));
        }

        // of customer entry
        Customer customer = accountRepository.createCustomer(registrationData.getName(), registrationData.getEmail(), registrationData.getPhone(), registrationData.getCountry(), registrationData.getContactPerson(), registrationData.getAddress1(), registrationData.getAddress2(), registrationData.getZip(), registrationData.getCity(), registrationData.getOrgNo());

        Account account = new Account(customer.getCustomerId(), customer.getName(), new UserName(registrationData.getUsername()), null, null, null, false, true);

        // of account entry and account_receiver entry (only if registering in SMP)
        //Prefix given orgNo with 9908
        ParticipantIdentifier participantId = registrationData.isRegisterSmp() ? ParticipantIdentifier.of(RingoConstant.NORWEGIAN_PEPPOL_PARTICIPANT_PREFIX + registrationData.getOrgNo()) : null;
        Account storedAccount = accountRepository.createAccount(account, participantId);

        // Encrypts/hashes the password
        String mutatedPassword = credentialHandler.mutate(registrationData.getPassword());

        // update account entry with password
        accountRepository.updatePasswordOnAccount(storedAccount.getAccountId(), mutatedPassword);

        // FIXME: add code here if you want to register party with SMP
        if (registrationData.isRegisterSmp()) {
            logger.info(String.format("Registering %s, with orgNo %s at SMP is not implemented", registrationData.getName(), registrationData.getOrgNo()));
        } else {
            logger.debug(String.format("Skipping registering %s, with orgNo %s at SMP", registrationData.getName(), registrationData.getOrgNo()));
        }

        // send info to sales department that new customer has been registered
        emailService.sendRegistrationNotification(account, registrationData.isRegisterSmp() ? "account will receive" : "account will only transmit");

        //if difi registration happened
        if (registrationData.isRegisterSmp()) {
            return new RegistrationProcessResult(RegistrationProcessResult.RegistrationSource.DIFI, true, MessageHelper.getMessage("difi.reg.successful"));
        } else {
            return new RegistrationProcessResult(RegistrationProcessResult.RegistrationSource.RINGO, true, MessageHelper.getMessage("reg.successful"));
        }

    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().length() == 0;
    }

    /**
     * package protected for tests
     *
     * @param accountRepository
     */
    void setAccountRepository(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

}
