package no.sr.ringo.message;

import com.google.inject.Inject;
import no.difi.oxalis.api.model.TransmissionIdentifier;
import no.difi.vefa.peppol.common.model.DocumentTypeIdentifier;
import no.difi.vefa.peppol.common.model.ParticipantIdentifier;
import no.difi.vefa.peppol.common.model.ProcessIdentifier;
import no.difi.vefa.peppol.common.model.Receipt;
import no.sr.ringo.account.Account;
import no.sr.ringo.account.AccountId;
import no.sr.ringo.message.statistics.InboxStatistics;
import no.sr.ringo.message.statistics.OutboxStatistics;
import no.sr.ringo.message.statistics.RingoAccountStatistics;
import no.sr.ringo.message.statistics.RingoStatistics;
import no.sr.ringo.peppol.ChannelProtocol;
import no.sr.ringo.peppol.PeppolChannelId;
import no.sr.ringo.peppol.PeppolHeader;
import no.sr.ringo.persistence.guice.jdbc.JdbcTxManager;
import no.sr.ringo.persistence.guice.jdbc.Repository;
import no.sr.ringo.persistence.jdbc.platform.DbmsPlatform;
import no.sr.ringo.persistence.jdbc.platform.DbmsPlatformFactory;
import no.sr.ringo.utils.SbdhUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static no.sr.ringo.transport.TransferDirection.OUT;

/**
 * Repository for the message meta data entities.
 * <p>
 * TODO: This class should be merged with {@link no.sr.ringo.persistence.jdbc.MessageRepositoryH2Impl}
 *
 * @author Steinar Overbeck Cook steinar@sendregning.no
 * @author Thore Holmberg Johnsen thore@sendregning.no
 */
@Repository
public class PeppolMessageRepositoryImpl implements PeppolMessageRepository {

    static final Logger log = LoggerFactory.getLogger(PeppolMessageRepositoryImpl.class);

    final JdbcTxManager jdbcTxManager;

    // From oxalis-persistence
    private final MessageRepository oxalisMessageRepository;

    @Inject
    public PeppolMessageRepositoryImpl(JdbcTxManager jdbcTxManager, MessageRepository oxalisMessageRepository) {
        this.jdbcTxManager = jdbcTxManager;
        this.oxalisMessageRepository = oxalisMessageRepository;
    }

    /**
     * Inserts or updates the supplied PEPPOL message to the database
     */
    @Override
    public MessageWithLocations persistOutboundMessage(Account account, PeppolMessage peppolMessage) {

        Connection con;
        if (account == null) {
            throw new IllegalStateException("SrAccountId property of message is required");
        }

        PeppolHeader peppolHeader = peppolMessage.getPeppolHeader();


        final MessageMetaDataImpl mmd = new MessageMetaDataImpl();
        mmd.setPeppolHeader(peppolHeader);
        mmd.setTransferDirection(OUT);
        mmd.getPeppolHeader().setPeppolChannelId(new PeppolChannelId(ChannelProtocol.SREST.name()));
        mmd.setAccountId(account.getAccountId());


        // Delegates to the injected message repository
        Long msgNo = null;
        msgNo = oxalisMessageRepository.saveOutboundMessage(mmd, peppolMessage.getXmlMessage());

        mmd.setMsgNo(MessageNumber.of(msgNo));

        return new MessageWithLocationsImpl(mmd);
    }


    DbmsPlatform getDbmsPlatform() {
        return DbmsPlatformFactory.platformFor(jdbcTxManager.getConnection());
    }

    @Override
    public MessageMetaData findMessageByMessageNo(MessageNumber msgNo) throws PeppolMessageNotFoundException {
        try {
            Connection connection = jdbcTxManager.getConnection();

            SqlHelper sql = SqlHelper.create(getDbmsPlatform()).findMessageByMessageNo();

            PreparedStatement ps = sql.prepareStatement(connection);
            ps.setInt(1, msgNo.toInt());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return extractMessageFromResultSet(rs);
            } else {
                throw new PeppolMessageNotFoundException(msgNo);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to retrieve xml document for message no: " + msgNo, e);
        }
    }

    @Override
    public MessageMetaData findMessageByMessageNo(Account account, MessageNumber messageNo) throws PeppolMessageNotFoundException {
        try {

            SqlHelper sql = SqlHelper.create(getDbmsPlatform()).findMessageByMessageNoAndAccountId();
            
            PreparedStatement ps = sql.prepareStatement(jdbcTxManager.getConnection());
            ps.setLong(1, messageNo.toLong());
            ps.setInt(2, account.getAccountId().toInteger());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return extractMessageFromResultSet(rs);
            } else {
                throw new PeppolMessageNotFoundException(messageNo);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to retrieve xml document for message no: " + messageNo, e);
        }
    }

    @Override
    public Integer getInboxCount(AccountId accountId) {
        Integer result = 0;
        try {
            SqlHelper sql = SqlHelper.create(getDbmsPlatform()).inboxCount();
            Connection connection = jdbcTxManager.getConnection();
            PreparedStatement ps = sql.prepareStatement(connection);
            ps.setInt(1, accountId.toInteger());
            ps.setString(2, no.sr.ringo.transport.TransferDirection.IN.name());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                result = rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to get count", e);
        }
        return result;
    }

    @Override
    public List<MessageMetaData> findUndeliveredOutboundMessagesByAccount(AccountId accountId) {
        return findUndeliveredMessagesByAccount(accountId, OUT);
    }

    @Override
    public List<MessageMetaData> findUndeliveredInboundMessagesByAccount(AccountId accountId) {
        return findUndeliveredMessagesByAccount(accountId, no.sr.ringo.transport.TransferDirection.IN);
    }

    /**
     * Helper method for finding undelivered messages, which are either outbound or inbound.
     */
    List<MessageMetaData> findUndeliveredMessagesByAccount(AccountId accountId, no.sr.ringo.transport.TransferDirection transferDirection) {
        final SqlHelper sql = SqlHelper.create(getDbmsPlatform()).undeliveredMessagesSql(transferDirection);
        try {
            PreparedStatement ps = sql.prepareStatement(jdbcTxManager.getConnection());
            ps.setInt(1, accountId.toInteger());
            ps.setString(2, transferDirection.name());
            ResultSet rs = ps.executeQuery();
            return fetchAllMessagesFromResultSet(rs);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to get messages", e);
        }
    }

    @Override
    public List<MessageMetaData> findMessages(AccountId accountId, MessageSearchParams searchParams) {
        try {
            SqlHelper sql = SqlHelper.create(getDbmsPlatform()).findMessages(searchParams);
            PreparedStatement ps = sql.prepareStatement(jdbcTxManager.getConnection());
            ps.setInt(1, accountId.toInteger());
            ResultSet rs = ps.executeQuery();
            return fetchAllMessagesFromResultSet(rs);
        } catch (SQLException e) {
            throw new IllegalStateException("Message search failed", e);
        }
    }

    @Override
    public Integer getMessagesCount(AccountId accountId, MessageSearchParams searchParams) {
        SqlHelper sql = SqlHelper.create(getDbmsPlatform()).messagesCount(searchParams);
        Integer result = 0;
        try {
            PreparedStatement ps = sql.prepareStatement(jdbcTxManager.getConnection());
            ps.setInt(1, accountId.toInteger());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                result = rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Message count failed", e);
        }
        return result;
    }

    @Override
    public Integer getMessagesCount(AccountId accountId) {
        Connection con;
        Integer result = 0;
        try {
            con = jdbcTxManager.getConnection();
            PreparedStatement ps = con.prepareStatement("select count(*) from message where account_id=?");
            ps.setInt(1, accountId.toInteger());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                result = rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Message count failed", e);
        }
        return result;
    }

    @Override
    public void markMessageAsRead(Long messageNo) {
        Connection con;
        String sql = "update message set delivered = ? where msg_no = ?";
        try {
            con = jdbcTxManager.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setTimestamp(1, new Timestamp(new Date().getTime()));
            ps.setLong(2, messageNo);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Marking message as read failed", e);
        }
    }

    @Override
    public List<MessageMetaData> findMessagesWithoutAccountId() {
        Connection con;
        List<MessageMetaData> metaData = new ArrayList<MessageMetaData>();
        String mainSql = "select msg_no, direction, received, delivered, sender, receiver, channel, document_id, process_id, message_uuid from message where account_id is null ";
        try {
            con = jdbcTxManager.getConnection();
            PreparedStatement ps = con.prepareStatement(mainSql);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                MessageMetaDataImpl m = extractMessageForResultSetWithoutAccountId(rs);
                metaData.add(m);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Message search failed", e);
        }
        return metaData;
    }

    @Override
    public void updateOutBoundMessageDeliveryDateAndUuid(MessageNumber msgNo, URI remoteAP, ReceptionId receptionId, TransmissionIdentifier transmissionIdentifier, Date delivered, Receipt receipt) {

        // Persists the evidence, after which the DBMS is updated
        persistOutboundEvidence(receptionId, delivered, receipt);

        Connection con;
        String sql = "update message set delivered = ?, remote_host = ?, transmission_id = ? where msg_no = ?";
        try {
            con = jdbcTxManager.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setTimestamp(1, new Timestamp(delivered.getTime()));
            ps.setString(2, remoteAP != null ? remoteAP.toString() : null);
            ps.setString(3, transmissionIdentifier.getIdentifier());
            ps.setLong(4, msgNo.toLong());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(sql + " failed " + e, e);
        }
    }

    void persistOutboundEvidence(final ReceptionId receptionId, final Date delivered, final Receipt receipt) {

        oxalisMessageRepository.saveOutboundTransportReceipt(receipt, receptionId);
    }

    @Override
    public Long copyOutboundMessageToInbound(Long outMsgNo, ReceptionId re) {
        Connection con;
        String sql = "insert into message (account_id, direction, received, sender, receiver, channel, message_uuid, transmission_id, instance_id, document_id, process_id, remote_host, ap_name, payload_url, evidence_url) " +
                //                                                               1                                                                   2
                " (select account_id, 'IN', received, sender, receiver, channel, ?, transmission_id, instance_id, document_id, process_id, remote_host, ap_name, payload_url, evidence_url from message where msg_no = ?);";
        try {
            con = jdbcTxManager.getConnection();
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, re.stringValue());
            ps.setLong(2, outMsgNo);
            ps.execute();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                Long msgNo = rs.getLong(1);
                return msgNo;
            } else {
                throw new IllegalStateException("Unable to obtain generated key after insert.");
            }
        } catch (SQLException e) {
            throw new IllegalStateException(sql + " failed " + e, e);
        }
    }

    @Override
    public String findDocumentByMessageNoWithoutAccountCheck(Long messageNo) throws PeppolMessageNotFoundException {
        Connection con;
        String xmlMessage;
        try {
            con = jdbcTxManager.getConnection();
            PreparedStatement ps = con.prepareStatement("select payload_url from message where msg_no=?");
            ps.setLong(1, messageNo);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String payloadUrl = SbdhUtils.removeSbdhEnvelope(rs.getString("payload_url"));

                try (Stream<String> lines = Files.lines(Paths.get(URI.create(payloadUrl)), Charset.forName("UTF-8"))) {
                    xmlMessage = lines.collect(joining(System.lineSeparator()));
                }
            } else
                throw new PeppolMessageNotFoundException(MessageNumber.of(messageNo));
            return SbdhUtils.removeSbdhEnvelope(xmlMessage);
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Unable to retrieve xml document for message no: " + messageNo, e);
        }
    }

    @Override
    public boolean isSenderAndReceiverAccountTheSame(Long messageNo) {
        String query = "select" +
                "        EXISTS" +
                "                (select 1 from account_receiver ar, message m" +
                "                        where m.msg_no = ?" +
                "                        and m.receiver = ar.participant_id" +
                "                        and m.account_id = ar.account_id)" +
                "                 as same_account;";
        Connection con;
        Boolean same_account;
        try {
            con = jdbcTxManager.getConnection();
            PreparedStatement ps = con.prepareStatement(query);
            ps.setLong(1, messageNo);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                same_account = rs.getBoolean("same_account");
            } else
                throw new PeppolMessageNotFoundException(MessageNumber.of(messageNo));
            return same_account;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to retrieve xml document for message no: " + messageNo, e);
        }
    }

    @Override
    public RingoStatistics getAdminStatistics() {
        return getAccountStatistics(null);
    }

    @Override
    public RingoStatistics getAccountStatistics(AccountId accountId) {

        List<RingoAccountStatistics> accountStatistics = new ArrayList<RingoAccountStatistics>();

        try {
            Connection con = jdbcTxManager.getConnection();
            final String selectSql =

                    "-- Provides statistics for a given account.  \n" +
                    "select\n" +
                    "    account.id,\n" +
                    "    account.name,\n" +
                    "    customer.contact_email,\n" +
                    "    -- total number of messages received regardless of direction\n" +
                    "    count(*) as \"total\",\n" +
                    "    -- total number of outbound messages\n" +
                    "    sum(case when message.direction = 'OUT' then 1 else 0 end) as \"out\",\n" +
                    "    -- number of outbound messages not delivered\n" +
                    "    sum(case when message.direction = 'OUT' and message.delivered is null then 1 else 0 end ) as \"undelivered out\",\n" +
                    "    -- timestamp of last outbound transmission\n" +
                    "    max(case when message.direction = 'OUT' then message.delivered else null end) \"last sent\",\n" +
                    "    -- timestamp of last outbound message received\n" +
                    "    max(case when message.direction = 'OUT' then message.received else null end) \"last received out\",\n" +
                    "    -- total number of inbound messages\n" +
                    "    sum(case when message.direction = 'IN' then 1 else 0 end) as \"in\",\n" +
                    "    -- total number of inbound messages not delivered to end user \n" +
                    "    sum(case when message.direction = 'IN' and message.delivered is null then 1 else 0 end ) as \"undelivered in\",\n" +
                    "    -- timestamp of last inbound message delivered\n" +
                    "    max(case when message.direction = 'IN' then message.delivered else null end) \"last downloaded\",\n" +
                    "    -- timestamp of last inbound reception\n" +
                    "    max(case when message.direction = 'IN' then message.received else null end) \"last received in\",\n" +
                    "    -- timestamp of oldest inbound message not delivered\n" +
                    "    min(case when message.direction = 'IN' and message.delivered is null then message.received else null end) \"oldest undelivered in\"\n" +
                    "from\n" +
                    "    account\n" +
                    "    left outer join message on account.id = message.account_id\n" +
                    "    left outer join customer on customer.id = account.customer_id"
                    ;

            // If an accountId is provided the where clause should
            // restrict the result set to that where clause
            final String whereClause;
            if (accountId != null) {
                whereClause = " where account.id = ? ";
            } else {
                whereClause = "";
            }

            final String groupBy = "\nGROUP BY account.id, account.name, customer.contact_email ";
            final String orderBy = "\nORDER BY account.name ASC ";

            //generates the sqlStatement
            final String sql = selectSql + whereClause + groupBy + orderBy;

            log.debug("Executing:\n{}", sql);
            
            PreparedStatement ps = con.prepareStatement(sql);
            //if we have an account id it needs to be provided to the statement
            if (accountId != null) {
                ps.setInt(1, accountId.toInteger());
            }

            //executes the sql and iterates the result set creating statistics for each account
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                final RingoAccountStatistics statistics = extractAccountStatistics(rs);
                accountStatistics.add(statistics);

            }

        } catch (SQLException e) {
            throw new IllegalStateException("Unable to get ringo statistics", e);
        }

        return new RingoStatistics(accountStatistics);

    }

    private List<MessageMetaData> fetchAllMessagesFromResultSet(ResultSet rs) throws SQLException {
        List<MessageMetaData> metaData = new ArrayList<MessageMetaData>();
        while (rs.next()) {
            metaData.add(extractMessageFromResultSet(rs));
        }
        return metaData;
    }

    private MessageMetaDataImpl extractMessageFromResultSet(ResultSet rs) throws SQLException {
        MessageMetaDataImpl messageMetaData = new MessageMetaDataImpl();
        final long msg_no = rs.getLong("msg_no");
        messageMetaData.setMsgNo(MessageNumber.of(msg_no));
        messageMetaData.setAccountId(new AccountId(rs.getInt("account_id")));
        messageMetaData.setTransferDirection(no.sr.ringo.transport.TransferDirection.valueOf(rs.getString("direction")));
        messageMetaData.setReceived(rs.getTimestamp("received"));
        messageMetaData.setDelivered(rs.getTimestamp("delivered"));
        messageMetaData.getPeppolHeader().setSender(ParticipantIdentifier.of(rs.getString("sender")));

        String receiverAsString = rs.getString("receiver");
        ParticipantIdentifier receiver = ParticipantIdentifier.of(receiverAsString);

        messageMetaData.getPeppolHeader().setReceiver(receiver);

        messageMetaData.getPeppolHeader().setPeppolChannelId(new PeppolChannelId(rs.getString("channel")));
        String message_uuid = rs.getString("message_uuid");
        if (message_uuid != null) {
            messageMetaData.setReceptionId(new ReceptionId(message_uuid));
        }

        final String transmission_id = rs.getString("transmission_id");
        if (transmission_id != null) {
            messageMetaData.setTransmissionId(TransmissionIdentifier.of(transmission_id));
        }
        messageMetaData.getPeppolHeader().setDocumentTypeIdentifier(DocumentTypeIdentifier.of(rs.getString("document_id")));

        String processId = rs.getString("process_id");
        if (processId != null) {
            messageMetaData.getPeppolHeader().setProcessIdentifier(ProcessIdentifier.of(processId));
        }

        final String payload_url = rs.getString("payload_url");
        try {
            if (payload_url != null) {
                messageMetaData.setPayloadUri(new URI(payload_url));
            }
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Invalid payload_url: '" + payload_url + "' for msg_no=" + msg_no);
        }
        
        return messageMetaData;
    }


    private MessageMetaDataImpl extractMessageForResultSetWithoutAccountId(ResultSet rs) throws SQLException {
        MessageMetaDataImpl m = new MessageMetaDataImpl();
        m.setMsgNo(MessageNumber.of(rs.getLong("msg_no")));
        m.setTransferDirection(no.sr.ringo.transport.TransferDirection.valueOf(rs.getString("direction")));
        m.setReceived(rs.getTimestamp("received"));
        m.setDelivered(rs.getTimestamp("delivered"));
        m.getPeppolHeader().setSender(ParticipantIdentifier.of(rs.getString("sender")));
        m.getPeppolHeader().setReceiver(ParticipantIdentifier.of(rs.getString("receiver")));
        m.getPeppolHeader().setPeppolChannelId(new PeppolChannelId(rs.getString("channel")));
        m.getPeppolHeader().setDocumentTypeIdentifier(DocumentTypeIdentifier.of(rs.getString("document_id")));
        m.getPeppolHeader().setProcessIdentifier( ProcessIdentifier.of(rs.getString("process_id")));

        // UUIDs are heavy lifting, check for null values first.
        String uuidString = rs.getString("message_uuid");
        if (!rs.wasNull()) {
            m.setReceptionId(new ReceptionId(uuidString));
        }
        return m;
    }

    private RingoAccountStatistics extractAccountStatistics(ResultSet rs) throws SQLException {
        //get the inbox statistics
        int in = rs.getInt("in");
        int undeliveredIn = rs.getInt("undelivered in");
        final Timestamp lastDownloadedTs = rs.getTimestamp("last downloaded");
        Date lastDownloaded = lastDownloadedTs == null ? null : new Date(lastDownloadedTs.getTime());

        final Timestamp lastReceivedInTs = rs.getTimestamp("last received in");
        Date lastReceivedIn = lastReceivedInTs == null ? null : new Date(lastReceivedInTs.getTime());

        final Timestamp oldestUndeliveredInTs = rs.getTimestamp("oldest undelivered in");
        Date oldestUndeliveredIn = oldestUndeliveredInTs == null ? null : new Date(oldestUndeliveredInTs.getTime());

        final InboxStatistics inboxStatistics = new InboxStatistics(in, undeliveredIn, lastDownloaded, lastReceivedIn, oldestUndeliveredIn);

        //extract the outbox statistcs
        int total = rs.getInt("total");
        int out = rs.getInt("out");
        int undeliveredOut = rs.getInt("undelivered out");

        final Timestamp lastSentTs = rs.getTimestamp("last sent");
        Date lastSent = lastSentTs == null ? null : new Date(lastSentTs.getTime());

        final Timestamp lastReceivedOutTs = rs.getTimestamp("last received out");
        Date lastReceivedOut = lastReceivedOutTs == null ? null : new Date(lastReceivedOutTs.getTime());

        final OutboxStatistics outboxStatistics = new OutboxStatistics(out, undeliveredOut, lastSent, lastReceivedOut);

        //extract the account details
        int accountIdint = rs.getInt("id");
        String accountName = rs.getString("name");
        String contactEmail = rs.getString("contact_email");

        return new RingoAccountStatistics(total, inboxStatistics, outboxStatistics, new AccountId(accountIdint), accountName, contactEmail);
    }

    /**
     * Helps creating SQL statements
     */
    private static class SqlHelper {

        private final DbmsPlatform dbmsPlatform;
        private String sql;

        private SqlHelper(DbmsPlatform dbmsPlatform) {
            this.dbmsPlatform = dbmsPlatform;
        }

        public static SqlHelper create(DbmsPlatform dbmsPlatform) {
            return new SqlHelper(dbmsPlatform);
        }

        public SqlHelper inboxCount() {
            sql = "select count(*) from message where account_id=? and direction= ? and delivered is null";
            return this;
        }

        private SqlHelper undeliveredMessagesSql(no.sr.ringo.transport.TransferDirection transferDirection) {

            String limitCondition = dbmsPlatform.getLimitClause(0, DEFAULT_PAGE_SIZE);


            if (no.sr.ringo.transport.TransferDirection.IN.equals(transferDirection)) {
                // Delivered must be null and uuid must not be null for valid undelivered incoming messages
                sql = selectMessage() +
                        "where delivered is null and message_uuid is not null and account_id=? and direction=? order by msg_no " + limitCondition;
            } else {
                // Delivered must be null for valid undelivered outgoing messages
                sql = selectMessage() +
                        "where delivered is null " +
                        "and account_id=? and direction=? " +
                        "and not exists(select 1 from outbound_message_queue omq where omq.msg_no = message.msg_no and omq.state='AOD') order by msg_no " +
                        limitCondition;
            }
            return this;
        }

        public SqlHelper findMessageByMessageNoAndAccountId() {
            sql = selectMessage() + " where msg_no=? and account_id=?";
            return this;
        }

        public SqlHelper findMessageByMessageNo() {
            sql = selectMessage() + " where msg_no=? ";
            return this;
        }

        public PreparedStatement prepareStatement(Connection connection) throws SQLException {
            log.debug("JDBC Connection URL {}", connection.getMetaData().getURL());
            log.debug("Preparing " + sql);
            return connection.prepareStatement(sql);
        }

        public SqlHelper findMessages(MessageSearchParams searchParams) {
            sql = selectMessage() + "where account_id=? ";
            generateWhereClause(searchParams);
            sql = sql.concat(" order by msg_no ");
            generateLimitCondition(searchParams.getPageIndex());
            return this;
        }

        private String selectMessage() {
            return "select account_id, msg_no, direction, received, delivered, sender, receiver, channel, document_id, process_id, message_uuid, transmission_id, payload_url from message ";
        }

        private String generateWhereClause(MessageSearchParams searchParams) {
            if (searchParams.getDirection() != null) {
                sql = sql.concat(" and direction = '" + searchParams.getDirection().name() + "'");
            }
            if (searchParams.getSender() != null) {
                sql = sql.concat(" and sender = '" + searchParams.getSender().getIdentifier() + "'");
            }
            if (searchParams.getReceiver() != null) {
                sql = sql.concat(" and receiver = '" + searchParams.getReceiver().getIdentifier() + "'");
            }
            if (searchParams.getSent() != null && searchParams.getDateCondition() != null) {
                // Mysql:  sql = sql.concat(" and Date(received) " + searchParams.getDateCondition().getValue() + "'" + searchParams.getSent() + "'");
                sql = sql.concat(" and FORMATDATETIME(received,'yyyy-MM-dd') " + searchParams.getDateCondition().getValue() + "'" + searchParams.getSent() + "'");
            }
            return sql;
        }

        private String generateLimitCondition(Integer pageIndex) {
            String limit = "";
            int offset = 0;
            if (pageIndex != null) {
                //first page should have offset 0, second one 25 etc...so subtracting 1 before multiplication
                offset = (pageIndex - 1) * PeppolMessageRepository.DEFAULT_PAGE_SIZE;
            }


            String limitClause = dbmsPlatform.getLimitClause(offset, DEFAULT_PAGE_SIZE);
            sql = sql.concat(" ").concat(limitClause);
            return sql;
        }

        public SqlHelper messagesCount(MessageSearchParams searchParams) {
            sql = "select count(*) from message where account_id=?";
            generateWhereClause(searchParams);
            return this;
        }

    }

}

