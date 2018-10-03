/*
 * Copyright 2010-2017 Norwegian Agency for Public Management and eGovernment (Difi)
 *
 * Licensed under the EUPL, Version 1.1 or – as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 *
 * You may not use this work except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/community/eupl/og_page/eupl
 *
 * Unless required by applicable law or agreed to in
 * writing, software distributed under the Licence is
 * distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

/* Created by steinar on 08.01.12 at 21:46 */
package no.sr.ringo.persistence.jdbc.util;

import com.google.inject.Inject;
import no.difi.vefa.peppol.common.model.DocumentTypeIdentifier;
import no.difi.vefa.peppol.common.model.ParticipantIdentifier;
import no.difi.vefa.peppol.common.model.ProcessIdentifier;
import no.sr.ringo.account.*;
import no.sr.ringo.message.*;
import no.sr.ringo.peppol.ChannelProtocol;
import no.sr.ringo.peppol.PeppolChannelId;
import no.sr.ringo.persistence.guice.jdbc.JdbcTxManager;
import no.sr.ringo.persistence.guice.jdbc.Repository;
import no.sr.ringo.persistence.queue.OutboundMessageQueueErrorId;
import no.sr.ringo.persistence.queue.OutboundMessageQueueId;
import no.sr.ringo.persistence.queue.OutboundMessageQueueState;
import no.sr.ringo.persistence.queue.QueuedOutboundMessageError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static no.sr.ringo.transport.TransferDirection.IN;
import static no.sr.ringo.transport.TransferDirection.OUT;

/**
 * Class providing helper methods to of messages and accounts for testing purposes.
 * <p>
 * It can be either extended by other integration test or used in http tests
 *
 * @author Adam Mscisz adam@sendregning.no
 * @deprecated use the appropriate Repository classes as this class has numerous unexpected side effects
 */
@Repository
public class DatabaseHelper {

    public static final Logger log = LoggerFactory.getLogger(DatabaseHelper.class);
    private final AccountRepository accountRepository;
    private final JdbcTxManager jdbcTxManager;

    // General persistence layer
    private final MessageRepository messageRepository;

    @Inject
    public DatabaseHelper(AccountRepository accountRepository, JdbcTxManager jdbcTxManager, MessageRepository messageRepository) {
        this.accountRepository = accountRepository;
        this.jdbcTxManager = jdbcTxManager;
        this.messageRepository = messageRepository;
    }


    public Long createSampleMessage(DocumentTypeIdentifier documentId, ProcessIdentifier processTypeId, String message, Integer accountId, no.sr.ringo.transport.TransferDirection direction,
                                    String senderValue, String receiverValue,
                                    final ReceptionId receptionId, Date delivered, Date received) {

        verifyReceptionId(receptionId);

        return createSampleMessage(documentId, processTypeId, message, accountId, direction, senderValue, receiverValue, receptionId, delivered, received, new PeppolChannelId(ChannelProtocol.SREST.name()));
    }

    void verifyReceptionId(ReceptionId receptionId) {
        if (receptionId == null) {
            throw new IllegalArgumentException("ReceptionId required argument");
        }
    }

    public Long createSampleMessage(DocumentTypeIdentifier documentId, ProcessIdentifier processTypeId, String message, Integer accountId, no.sr.ringo.transport.TransferDirection direction,
                                    String senderValue, String receiverValue,
                                    final ReceptionId receptionId, Date delivered, Date received,
                                    PeppolChannelId peppolChannelId) {

        if (received == null) {
            throw new IllegalArgumentException("received date is required");
        }

        verifyReceptionId(receptionId);

        final MessageMetaDataImpl mmd = new MessageMetaDataImpl();
        mmd.getPeppolHeader().setDocumentTypeIdentifier(documentId);
        mmd.getPeppolHeader().setProcessIdentifier(processTypeId);
        if (accountId != null) {
            mmd.setAccountId(new AccountId(accountId));
        }
        mmd.setTransferDirection(direction);
        mmd.getPeppolHeader().setSender(ParticipantIdentifier.of(senderValue));
        mmd.getPeppolHeader().setReceiver(ParticipantIdentifier.of(receiverValue));
        mmd.getPeppolHeader().setPeppolChannelId(peppolChannelId);

        mmd.setReceptionId(receptionId);
        mmd.setDelivered(delivered);
        mmd.setReceived(received);


        if (direction == IN) {
            return messageRepository.saveInboundMessage(mmd, new ByteArrayInputStream(message.getBytes(Charset.forName("UTF-8"))));
        } else if (direction == OUT) {
            return messageRepository.saveOutboundMessage(mmd, new ByteArrayInputStream(message.getBytes(Charset.forName("UTF-8"))));
        } else
            throw new IllegalStateException("No support for transfer direction " + mmd.getTransferDirection().name());

    }

    /**
     * Helper method creating simple sample message. If the direction is {@link no.sr.ringo.transport.TransferDirection#IN} the accountId
     * parameter will be ignored. The accountId will be set based upon the contents of the {@code account_receiver} table in
     * the database.
     *
     * @param direction indicates whether the message is inbound or outbound with respect to the PEPPOL network.
     */
    public Long createSampleMessage(Integer accountId, no.sr.ringo.transport.TransferDirection direction, String senderValue, String receiverValue, final ReceptionId receptionId, Date delivered, DocumentTypeIdentifier peppolDocumentTypeId, ProcessIdentifier peppolProcessTypeId) {
        DocumentTypeIdentifier invoiceDocumentType = peppolDocumentTypeId;
        ProcessIdentifier processTypeId = peppolProcessTypeId;

        return createSampleMessage(invoiceDocumentType, processTypeId, "<test>\u00E5</test>", accountId, direction, senderValue, receiverValue, receptionId, delivered, new Date(), new PeppolChannelId("UnitTest"));
    }

    public MessageNumber createSampleEntry(MessageMetaDataImpl tmd) {
        if (tmd == null) {
            throw new IllegalArgumentException("Missing required argument");
        }

        TransmissionMetaDataValidator.validate(tmd);

        String sql = "insert into message"
                //     1           2           3        4         5       6        7           8
                + "(account_id, direction, received, delivered, sender, receiver, channel, message_uuid," +
                //    9              10            11           12           13            14
                " transmission_id, instance_id, document_id, process_id, payload_url, evidence_url) " +
                " values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection con = jdbcTxManager.getConnection()) {

            final PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            if (tmd.getAccountId() == null) {
                ps.setNull(1, Types.INTEGER);
            } else
                ps.setInt(1, tmd.getAccountId().toInteger());

            ps.setString(2, tmd.getTransferDirection().name());
            ps.setTimestamp(3, new Timestamp(tmd.getReceived().getTime()));

            if (tmd.getDelivered() != null) {
                ps.setTimestamp(4, new Timestamp(tmd.getDelivered().getTime()));
            } else
                ps.setTimestamp(4, null);

            ps.setString(5, tmd.getPeppolHeader().getSender().getIdentifier());
            ps.setString(6, tmd.getPeppolHeader().getReceiver().getIdentifier());
            ps.setString(7, tmd.getPeppolHeader().getPeppolChannelId() != null ?
                    tmd.getPeppolHeader().getPeppolChannelId().stringValue() : null);
            ps.setString(8, tmd.getReceptionId().stringValue());
            ps.setString(9, tmd.getTransmissionId() != null ? tmd.getTransmissionId().getIdentifier() : null);
            ps.setString(10, tmd.getSbdhInstanceIdentifier() != null ?
                    tmd.getSbdhInstanceIdentifier().getIdentifier() : null);
            ps.setString(11, tmd.getPeppolHeader().getPeppolDocumentTypeId().getIdentifier());
            ps.setString(12, tmd.getPeppolHeader().getProcessIdentifier().getIdentifier());
            ps.setString(13, tmd.getPayloadUri().toString());
            ps.setString(14, tmd.getEvidenceUri() != null ?
                    tmd.getEvidenceUri().toString() : null);

            ps.executeUpdate();
            if (con.getMetaData().supportsGetGeneratedKeys()) {
                final ResultSet rs = ps.getGeneratedKeys();
                if (rs != null && rs.next()) {
                    final long generatedKey = rs.getLong(1);
                    return MessageNumber.of(generatedKey);
                }
            } else {
                throw new IllegalStateException("Dbms " + con.getMetaData().getDatabaseProductName() + " does not support auto generated keys");
            }

            return null;    // Should never happen

        } catch (SQLException e) {
            throw new IllegalStateException("Unable to of sample entry using; " + tmd.toString() + " and " + sql, e);
        }
    }


    /**
     * Helper method to delete rows in message table
     *
     * @param msgNo
     */

    public void deleteMessage(Long msgNo) {

        if (msgNo == null) {
            return;
        }

        Connection con = null;
        String sql = "delete from message where msg_no = ?";

        try {
            con = jdbcTxManager.getConnection();

            PreparedStatement ps = con.prepareStatement(sql);
            ps.setLong(1, msgNo);

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new IllegalStateException(sql + " failed " + e, e);
        }
    }

    /**
     * Helper method updating received date on message
     *
     * @param date
     * @param msgNo
     */
    public void updateMessageDate(Date date, Long msgNo) {
        Connection con = null;
        String sql = "update message set received = ? where msg_no = ?";

        try {
            con = jdbcTxManager.getConnection();

            PreparedStatement ps = con.prepareStatement(sql);
            ps.setTimestamp(1, new Timestamp(date.getTime()));
            ps.setLong(2, msgNo);

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(sql + " failed " + e, e);
        }
    }


    public void deleteAllMessagesForAccount(Account account) {
        if (account == null || account.getAccountId() == null) {
            return;
        }

        Connection con = null;
        String sql = "delete from message where account_id = ?";

        try {
            con = jdbcTxManager.getConnection();

            // Delete artifacts first.
            PreparedStatement ps = con.prepareStatement("select * from message where account_id = ?");
            ps.setInt(1, account.getAccountId().toInteger());
            ResultSet rs = ps.executeQuery();
            deleteArtifacts(rs);
            ps.close();

            ps = con.prepareStatement(sql);
            ps.setInt(1, account.getAccountId().toInteger());

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new IllegalStateException(sql + " failed " + e, e);
        }
    }

    public void deleteAllMessagesWithoutAccountId() {
        Connection con = null;
        String sql = "delete from message where account_id is null";

        try {
            con = jdbcTxManager.getConnection();
            // Delete artifacts first.
            PreparedStatement ps = con.prepareStatement("select * from message where account_id is null");
            ResultSet rs = ps.executeQuery();
            deleteArtifacts(rs);
            ps.close();

            ps = con.prepareStatement(sql);

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new IllegalStateException(sql + " failed " + e, e);
        }
    }

    private void deleteIfExists(String payloadUrl) throws IOException {
        if (payloadUrl != null) {
            Files.deleteIfExists(Paths.get(URI.create(payloadUrl)));
        }
    }

    private void deleteArtifacts(ResultSet rs) {
        try {
            while (rs.next()) {
                deleteIfExists(rs.getString("payload_url"));
                deleteIfExists(rs.getString("evidence_url"));
            }
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Error while deleting artifacts" + e, e);
        }

    }

    public void updateMessageReceiver(Long msgNo, String receiver) {

        Connection con = null;
        String sql = "update message set receiver = ? where msg_no = ?";

        try {
            con = jdbcTxManager.getConnection();

            PreparedStatement ps = con.prepareStatement(sql);
            ps.setString(1, receiver);
            ps.setLong(2, msgNo);

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(sql + " failed " + e, e);
        }

    }

    public int addAccountReceiver(AccountId id, String receiver) {

        Connection con = null;
        String sql = "insert into account_receiver (account_id, participant_id) values(?,?)";

        try {
            con = jdbcTxManager.getConnection();
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, id.toInteger());
            ps.setString(2, receiver);

            ps.execute();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                int accountReceiverId = rs.getInt(1);

                return accountReceiverId;
            } else {
                throw new IllegalStateException("Unable to obtain generated key after insert.");
            }
        } catch (SQLException e) {
            throw new IllegalStateException(sql + " failed " + e, e);
        }
    }

    public void deleteAccountReceiver(Integer accountReceiverId) {
        if (accountReceiverId == null) {
            return;
        }

        Connection con = null;
        String sql = "delete from account_receiver where id = ?";

        try {
            con = jdbcTxManager.getConnection();

            PreparedStatement ps = con.prepareStatement(sql);
            ps.setInt(1, accountReceiverId);

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new IllegalStateException(sql + " failed " + e, e);
        }
    }

    public void deleteCustomer(Customer customer) {
        if (customer == null) {
            return;
        }
        Connection con = null;
        String sql = "delete from customer where id = ?";

        try {
            con = jdbcTxManager.getConnection();

            PreparedStatement ps = con.prepareStatement(sql);
            ps.setInt(1, customer.getId());

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new IllegalStateException(sql + " failed " + e, e);
        }
    }

    /**
     * Deletes all data related to an account.
     *
     * @param userNameToBeDeleted - it's both account.username and customer.name
     */
    public void deleteAccountData(UserName userNameToBeDeleted) {

        String userName = userNameToBeDeleted.stringValue();

        CustomerId customerIdToBeDeleted = null;

        Connection con = null;
        String sql = "delete from account_role where username = ?";

        try {
            con = jdbcTxManager.getConnection();

            PreparedStatement ps = con.prepareStatement(sql);
            ps.setString(1, userName);
            ps.executeUpdate();

            sql = "delete from account_receiver where account_id = (select id from account where username = ?)";
            ps = con.prepareStatement(sql);
            ps.setString(1, userName);
            ps.executeUpdate();

            sql = "select customer_id from account where username = ?";
            ps = con.prepareStatement(sql);
            ps.setString(1, userName);
            ResultSet resultSet = ps.executeQuery();
            if (resultSet.next()) {
                customerIdToBeDeleted = new CustomerId(resultSet.getInt(1));
            }

            sql = "delete from account where username = ?";
            ps = con.prepareStatement(sql);
            ps.setString(1, userName);
            ps.executeUpdate();

            if (customerIdToBeDeleted != null) {
                log.info("Removing customer account with id=" + customerIdToBeDeleted.toString());
                sql = "delete from customer where id = ?";
                ps = con.prepareStatement(sql);
                ps.setInt(1, customerIdToBeDeleted.toInteger());
                ps.executeUpdate();
            } else {
                log.info("No customer entry for username " + userName);
            }

        } catch (SQLException e) {
            throw new IllegalStateException(sql + " failed " + e, e);
        }
    }

    /**
     * @return true if account has client role in account_role table
     */
    public boolean hasClientRole(UserName userName) {

        Connection con = null;
        String sql = "select count(*) from account_role where username like ? and role_name ='client'";

        try {
            con = jdbcTxManager.getConnection();

            PreparedStatement ps = con.prepareStatement(sql);
            ps.setString(1, userName.stringValue());

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            } else {
                return false;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(String.format("%s failed with username: %s", sql, userName), e);
        }
    }

    public boolean accountReceiverExists(AccountId id, String orgNo) {

        Connection con = null;
        String sql = "select count(*) from account_receiver where account_id = ? and participant_id = ?";

        try {
            con = jdbcTxManager.getConnection();

            PreparedStatement ps = con.prepareStatement(sql);
            ps.setInt(1, id.toInteger());
            ps.setString(2, "9908:".concat(orgNo));


            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            } else {
                return false;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(String.format("%s failed with orgNo: %s", sql, orgNo), e);
        }
    }

    public JdbcTxManager getJdbcTxManager() {
        return jdbcTxManager;
    }

    public QueuedMessage getQueuedMessageByQueueId(OutboundMessageQueueId queueId) {
        Connection con = null;
        String sql = "select * from outbound_message_queue where id = ?";

        try {
            con = jdbcTxManager.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setInt(1, queueId.toInt());

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new QueuedMessage(rs.getInt("id"), rs.getLong("msg_no"), OutboundMessageQueueState.valueOf(rs.getString("state")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("error fetching fault messages", e);
        }
        return null;
    }

    public QueuedMessage getQueuedMessageByMsgNo(Long msgNo) {
        Connection con = null;
        String sql = "select * from outbound_message_queue where msg_no = ?";

        try {
            con = jdbcTxManager.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setLong(1, msgNo);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new QueuedMessage(rs.getInt("id"), rs.getLong("msg_no"), OutboundMessageQueueState.valueOf(rs.getString("state")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("error fetching fault messages", e);
        }
        return null;
    }

    public Integer putMessageOnQueue(Long msgId) {
        Connection con = null;
        String sql = "insert into outbound_message_queue (msg_no, state) values (?,?)";

        try {
            con = jdbcTxManager.getConnection();
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, msgId);
            ps.setString(2, OutboundMessageQueueState.QUEUED.name());

            ps.execute();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            } else {
                throw new IllegalStateException("Unable to obtain generated key after insert.");
            }
        } catch (SQLException e) {
            throw new IllegalStateException(sql + " failed " + e, e);
        }
    }

    public List<QueuedOutboundMessageError> getErrorMessages() {
        List<QueuedOutboundMessageError> result = new ArrayList<QueuedOutboundMessageError>();

        Connection con = null;
        String sql = "select * from outbound_message_queue_error";

        try {
            con = jdbcTxManager.getConnection();
            PreparedStatement ps = con.prepareStatement(sql);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(new QueuedOutboundMessageError(new OutboundMessageQueueErrorId(rs.getInt("id")), new OutboundMessageQueueId(rs.getInt("queue_id")), null, rs.getString("message"), rs.getString("details"), rs.getString("stacktrace"), rs.getTimestamp("create_dt"), "1"));
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("error fetching fault messages", e);
        }
    }

    public void updateValidateFlagOnAccount(AccountId accountId, boolean validateUpdate) {
        Connection con = null;
        String sql = "update account set validate_upload= ? where id = ?";

        try {
            con = jdbcTxManager.getConnection();

            PreparedStatement ps = con.prepareStatement(sql);
            ps.setBoolean(1, validateUpdate);
            ps.setInt(2, accountId.toInteger());

            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(sql + " failed " + e, e);
        }
    }

    public void removeExistingErrorMessages() {
        Connection con = null;
        String sql = "delete from outbound_message_queue_error";

        try {
            con = jdbcTxManager.getConnection();

            PreparedStatement ps = con.prepareStatement(sql);

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new IllegalStateException(sql + " failed " + e, e);
        }
    }

    public class QueuedMessage {
        private final Integer queueId;
        private final Long msgNo;
        private final OutboundMessageQueueState status;

        public QueuedMessage(Integer queueId, Long msgNo, OutboundMessageQueueState status) {
            this.queueId = queueId;
            this.msgNo = msgNo;
            this.status = status;
        }

        public Long getMsgNo() {
            return msgNo;
        }

        public Integer getQueueId() {
            return queueId;
        }

        public OutboundMessageQueueState getState() {
            return status;
        }
    }
}
