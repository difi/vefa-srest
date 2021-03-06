/* Created by steinar on 08.01.12 at 21:46 */
package no.sr.ringo.persistence;

import com.google.inject.Inject;
import no.difi.oxalis.test.identifier.WellKnownParticipant;
import no.difi.vefa.peppol.common.model.ParticipantIdentifier;
import no.sr.ringo.ObjectMother;
import no.sr.ringo.account.Account;
import no.sr.ringo.account.AccountRepository;
import no.sr.ringo.guice.ServerTestModuleFactory;
import no.sr.ringo.message.MessageMetaData;
import no.sr.ringo.message.PeppolMessageRepository;
import no.sr.ringo.message.ReceptionId;
import no.sr.ringo.message.SearchParams;
import no.sr.ringo.persistence.jdbc.util.DatabaseHelper;
import no.sr.ringo.transport.TransferDirection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

import static org.testng.Assert.assertEquals;

/**
 * Integration test verifying that messages can be filtered by various params
 *
 * @author Adam Mscisz adam@sendregning.no
 */
@Guice(moduleFactory = ServerTestModuleFactory.class)
public class MessageSearchPersistenceTest {

    Logger logger = LoggerFactory.getLogger(MessageSearchPersistenceTest.class);

    final AccountRepository accountRepository;
    final PeppolMessageRepository peppolMessageRepository;
    final DatabaseHelper databaseHelper;
    private final DbmsTestHelper dbmsTestHelper;

    private Account account;
    private ParticipantIdentifier participantId;
    private Long firstMessageNo;
    private Long secondMessageNo;
    String receiver1 = WellKnownParticipant.DIFI.getIdentifier();
    String receiver2 = WellKnownParticipant.DIFI_TEST.getIdentifier();

    @Inject
    public MessageSearchPersistenceTest(AccountRepository accountRepository, PeppolMessageRepository peppolMessageRepository, DatabaseHelper databaseHelper, DbmsTestHelper dbmsTestHelper) {
        this.accountRepository = accountRepository;
        this.peppolMessageRepository = peppolMessageRepository;
        this.databaseHelper = databaseHelper;
        this.dbmsTestHelper = dbmsTestHelper;
    }

    @Test(groups = {"persistence"})
    public void testFindAllMessages() {
        List<MessageMetaData> messages = peppolMessageRepository.findMessages(account.getAccountId(), new SearchParams(null, null, null, null, null));

        //expect to find 2 messages created in setUp
        assertEquals(2, messages.size());
    }

    @Test(groups = {"persistence"})
    public void testFindBySender() {

        SearchParams searchParams = new SearchParams(null, participantId.getIdentifier(), null, null, null);

        List<MessageMetaData> messages = peppolMessageRepository.findMessages(account.getAccountId(), searchParams);

        //expect to find 2 messages for sender
        assertEquals(2, messages.size());
    }

    @Test(groups = {"persistence"})
    public void testFindBySenderAndReceiver() {

        final String sender = participantId.getIdentifier();
        SearchParams searchParams = new SearchParams(null, sender, receiver1, null, null);

        List<MessageMetaData> messages = peppolMessageRepository.findMessages(account.getAccountId(), searchParams);

        //expect to find 1 message for receiver1
        assertEquals(1, messages.size());

        searchParams = new SearchParams(null, sender, receiver2, null, null);

        messages = peppolMessageRepository.findMessages(account.getAccountId(), searchParams);

        //expect to find 1 message for receiver2
        assertEquals(1, messages.size());
    }

    @Test(groups = {"persistence"})
    public void testFindBySenderAndDirection() {
        final String sender = participantId.getIdentifier();
        SearchParams searchParams = new SearchParams("IN", sender, null, null, null);

        List<MessageMetaData> messages = peppolMessageRepository.findMessages(account.getAccountId(), searchParams);

        //expect to find 1 IN message
        assertEquals(1, messages.size());

        searchParams = new SearchParams("OUT", sender, receiver2, null, null);

        messages = peppolMessageRepository.findMessages(account.getAccountId(), searchParams);

        //expect to find 1 OUT message
        assertEquals(1, messages.size());
    }

    @Test(groups = {"persistence"})
    public void testFindBySenderReceiverAndDirection() {
        final String sender = participantId.getIdentifier();
        SearchParams searchParams = new SearchParams("in", sender, receiver1, null, null);

        List<MessageMetaData> messages = peppolMessageRepository.findMessages(account.getAccountId(), searchParams);

        //expect to find 1 IN message for receiver1
        assertEquals(1, messages.size());

        searchParams = new SearchParams("out", sender, receiver1, null, null);

        messages = peppolMessageRepository.findMessages(account.getAccountId(), searchParams);

        //expect to find no OUT messages for receiver1
        assertEquals(0, messages.size());
    }


    @Test(groups = {"persistence"})
    public void testFindByDate() {
        final String sender = participantId.getIdentifier();
        SearchParams searchParams = new SearchParams(null, sender, null, null, null);

        //find all messages
        List<MessageMetaData> messages = peppolMessageRepository.findMessages(account.getAccountId(), searchParams);
        assertEquals(2, messages.size());

        //they both have today's date, so let's construct the dateString in 'yyyy-MM-dd' format

        Calendar today = Calendar.getInstance();
        String todayString = convertCalendarToString(today);
        SearchParams.DateCondition dateCondition = SearchParams.DateCondition.EQUAL;

        //Let's add the date condition to the string, e.g. =|<=|>....
        String sentParam = dateCondition.getValue() + todayString;
        searchParams = new SearchParams(null, sender, null, sentParam, null);

        //looking for messages from today, so we still expect to find 2 of them
        messages = peppolMessageRepository.findMessages(account.getAccountId(), searchParams);
        assertEquals(2, messages.size());

        //Let's update one of the message's receive date to tomorrow and the other one to yesterday,
        Calendar yesterdayCal = Calendar.getInstance();
        yesterdayCal.add(Calendar.DATE, -1);

        Calendar tomorrowCal = Calendar.getInstance();
        tomorrowCal.add(Calendar.DATE, 1);

        String tomorrowString = convertCalendarToString(tomorrowCal);
        String yesterdayString = convertCalendarToString(yesterdayCal);

        databaseHelper.updateMessageDate(tomorrowCal.getTime(), firstMessageNo);
        databaseHelper.updateMessageDate(yesterdayCal.getTime(), secondMessageNo);

        searchParams = new SearchParams(null, sender, null, sentParam, null);
        //we expect to find no messages for today
        messages = peppolMessageRepository.findMessages(account.getAccountId(), searchParams);
        assertEquals(0, messages.size());

        //we do expect to have one for yesterday
        dateCondition = SearchParams.DateCondition.EQUAL;
        sentParam = dateCondition.getValue() + yesterdayString;
        searchParams = new SearchParams(null, sender, null, sentParam, null);
        messages = peppolMessageRepository.findMessages(account.getAccountId(), searchParams);
        assertEquals(1, messages.size());

        //the same result for <= today
        dateCondition = SearchParams.DateCondition.LESS_EQUAL;
        sentParam = dateCondition.getValue() + todayString;
        searchParams = new SearchParams(null, sender, null, sentParam, null);
        messages = peppolMessageRepository.findMessages(account.getAccountId(), searchParams);
        assertEquals(1, messages.size());

        //we do expect to have one for tomorrow
        dateCondition = SearchParams.DateCondition.EQUAL;
        sentParam = dateCondition.getValue() + tomorrowString;
        searchParams = new SearchParams(null, sender, null, sentParam, null);
        messages = peppolMessageRepository.findMessages(account.getAccountId(), searchParams);
        assertEquals(1, messages.size());

        //the same result for => today
        dateCondition = SearchParams.DateCondition.GREATER_EQUAL;
        sentParam = dateCondition.getValue() + todayString;
        searchParams = new SearchParams(null, sender, null, sentParam, null);
        messages = peppolMessageRepository.findMessages(account.getAccountId(), searchParams);
        assertEquals(1, messages.size());

    }



    /**
     * Helper method converting calendar to string
     *
     * @param today
     * @return
     */
    private String convertCalendarToString(Calendar today) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

        return formatter.format(today.getTime());
    }

    @BeforeMethod
    public void setUp() throws Exception {
        participantId = ObjectMother.getAdamsParticipantId();
        account = accountRepository.createAccount(ObjectMother.getAdamsAccount(), participantId);
        final String sender = participantId.getIdentifier();

        firstMessageNo = dbmsTestHelper.createSampleMessage(account.getAccountId().toInteger(), TransferDirection.IN, sender, receiver1, new ReceptionId(), null);
        secondMessageNo = dbmsTestHelper.createSampleMessage(account.getAccountId().toInteger(), TransferDirection.OUT, sender, receiver2, new ReceptionId(), null);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        databaseHelper.deleteAllMessagesForAccount(account);
        accountRepository.deleteAccount(account.getAccountId());
    }
}
