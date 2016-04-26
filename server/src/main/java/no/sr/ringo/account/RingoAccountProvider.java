package no.sr.ringo.account;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;

/**
 * Provides the RingoAccount using the java.security.Principal Object
 *
 * @author andy
 * @author thore
 */
public class RingoAccountProvider {

    static final Logger log = LoggerFactory.getLogger(RingoAccountProvider.class);
    private final AccountRepository accountRepository;

    @Inject
    RingoAccountProvider(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * Looks up the account specified in the Principal using the account repository
     *
     * @param principal the Principal from the request can be found using the PrincipalProvider
     * @return the RingoAccount associated with the given principal
     */
    public RingoAccount getAccount(Principal principal) {
        final UserName userName = new UserName(principal.getName());
        log.info("Processing request from " + userName);
        return accountRepository.findAccountByUsername(userName);
    }

}