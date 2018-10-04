package no.sr.ringo.usecase;

import com.google.inject.Inject;
import no.sr.ringo.account.Account;
import no.sr.ringo.account.AccountRepository;
import no.sr.ringo.email.EmailService;
import no.sr.ringo.message.MessageMetaData;
import no.sr.ringo.message.MessageNumber;
import no.sr.ringo.message.PeppolMessageRepository;
import no.sr.ringo.oxalis.PeppolDocumentSender;
import no.sr.ringo.persistence.guice.jdbc.Transactional;
import no.sr.ringo.persistence.queue.*;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Use case which gathers together functionality for sending <em>ONE</em> or <em>ALL</em> undelivered outbound messages
 *
 * @author andy
 * @author thore
 */
public class SendQueuedMessagesUseCase {

    public static final Logger LOGGER = LoggerFactory.getLogger(SendQueuedMessagesUseCase.class);

    private PeppolDocumentSender documentSender;
    private final PeppolMessageRepository messageRepository;
    private final QueueRepository queueRepository;
    private final EmailService emailService;
    private final AccountRepository accountRepository;

    private static Logger logger = LoggerFactory.getLogger(SendQueuedMessagesUseCase.class);

    @Inject
    public SendQueuedMessagesUseCase(PeppolDocumentSender documentSender, PeppolMessageRepository messageRepository, QueueRepository queueRepository, EmailService emailService, AccountRepository accountRepository) {
        this.documentSender = documentSender;
        this.messageRepository = messageRepository;
        this.queueRepository = queueRepository;
        this.emailService = emailService;
        this.accountRepository = accountRepository;
    }

    /**
     * Process queued messages in small batches, repeating for max 9 minutes (cron schedules new job each 10 minutes)
     * Also setting and returning proper status upon completion for messages sent
     */
    public QueuedMessageSenderResult handleAllQueuedMessages() {

        Map<OutboundMessageQueueId, String> failed = new HashMap<OutboundMessageQueueId, String>();
        int succeeded = 0;
        int skipped = 0;

        long maxBatchSize = 10;
        long maxSendDuration = 9 * 60 * 1000; // nine minutes as milliseconds

        long startTime = System.currentTimeMillis();
        boolean sendNextBatch = true;

        while (sendNextBatch) {
            List<QueuedOutboundMessage> queuedOutboundMessages = queueRepository.getQueuedMessages(maxBatchSize);
            if (queuedOutboundMessages.isEmpty()) {
                sendNextBatch = false;
            } else {
                for (QueuedOutboundMessage queuedOutboundMessage : queuedOutboundMessages) {
                    SingleProcessingResult singleProcessingResult = handleSingleQueuedMessage(queuedOutboundMessage);
                    if (singleProcessingResult.isSkipped()) {
                        skipped++;
                    } else if (singleProcessingResult.succeeded) {
                        succeeded++;
                    } else {
                        failed.put(queuedOutboundMessage.getOutboundQueueId(), singleProcessingResult.getErrorMessage());
                    }

                    if ((System.currentTimeMillis() - startTime) > maxSendDuration) {
                        sendNextBatch = false;
                        LOGGER.debug("Breaking execution after {}ms", maxSendDuration);
                        break;
                    }
                }
            }
        }

        return new QueuedMessageSenderResult(failed, succeeded, skipped);

    }

    /**
     * Handles specific queue item
     */
    public QueuedMessageSenderResult handleSingleQueuedMessage(OutboundMessageQueueId outboundQueueID) {

        Map<OutboundMessageQueueId, String> failed = new HashMap<OutboundMessageQueueId, String>();

        QueuedOutboundMessage queuedOutboundMessage = queueRepository.getQueuedMessageById(outboundQueueID);

        if (queuedOutboundMessage == null) {
            throw new IllegalStateException("Cannot find queued outbound message with id " + outboundQueueID.toString());
        }
        SingleProcessingResult singleProcessingResult = handleSingleQueuedMessage(queuedOutboundMessage);

        int succeeded = singleProcessingResult.isSucceeded() ? 1 : 0;
        int skipped = singleProcessingResult.isSkipped() ? 1 : 0;

        if (!singleProcessingResult.isSucceeded()) {
            failed.put(outboundQueueID, singleProcessingResult.getErrorMessage());
        }

        return new QueuedMessageSenderResult(failed, succeeded, skipped);
    }
    /**
     * Handles specific queued item.
     * If successful: message is updated with delivered and uuid and queue item is updated with OK state
     * If failure: queued item is updated with status AOD, error notification is sent
     */
    @Transactional
    private SingleProcessingResult handleSingleQueuedMessage(QueuedOutboundMessage queuedOutboundMessage) {

        boolean skipped = false;
        boolean succeeded = false;
        String errorMessage = null;

        MessageMetaData messageMetaData = messageRepository.findMessageByMessageNo(queuedOutboundMessage.getMessageNumber());
        if (messageMetaData == null) {
            errorMessage = String.format("Cannot find message with msgNo  %d", queuedOutboundMessage.getMessageNumber().toInt());
            logger.error(errorMessage);
            return new SingleProcessingResult(skipped, succeeded, errorMessage);
        }

        if (messageMetaData.getDelivered() != null) {
            errorMessage = String.format("Message with msgNo %d already delivered even though state is %s", queuedOutboundMessage.getMessageNumber().toInt(), queuedOutboundMessage.getState().name());
            logger.error(errorMessage);
            return new SingleProcessingResult(skipped, succeeded, errorMessage);
        }

        try {
            boolean deliveryOk = lockQueueRowAndSendMessage(messageMetaData, queuedOutboundMessage.getOutboundQueueId());
            if (deliveryOk) {
                queueRepository.changeQueuedMessageState(queuedOutboundMessage.getOutboundQueueId(), OutboundMessageQueueState.OK);
                logger.info(String.format("Queue item %d with messageNo %d sent - great success!", queuedOutboundMessage.getOutboundQueueId().toInt(), queuedOutboundMessage.getMessageNumber().toInt()));
                succeeded = true;
            } else {
                logger.info(String.format("Skipping queue item %d with messageNo %d sent - it is being sent by another process!", queuedOutboundMessage.getOutboundQueueId().toInt(), queuedOutboundMessage.getMessageNumber().toInt()));
                skipped = true;
            }
        } catch (Exception e) {
            errorMessage = String.format("Unable to process queue item %d with messageNo %d sent; %s", queuedOutboundMessage.getOutboundQueueId().toInt(), queuedOutboundMessage.getMessageNumber().toInt(), e.getMessage());
            handleException(queuedOutboundMessage, e);
        }

        return new SingleProcessingResult(skipped, succeeded, errorMessage);

    }

    /**
     * Marks the message as delivered and sends it updating with the UUID on completion.
     * @throws Exception If the message could not be sent. In which case the delivered timestamp is rolledback to NULL
     */
    @Transactional
    protected boolean lockQueueRowAndSendMessage(MessageMetaData messageMetaData, OutboundMessageQueueId outboundQueueID) throws Exception {

        // perform a selective update of the message row so that it in effect is locked (status changed to IN_PROGRESS)
        boolean ok = queueRepository.lockQueueItemForDelivery(outboundQueueID);

        // if ok == false someone else has already started to send this message
        // (this happens if 2 processes are iterating the same outbox)
        if (!ok) {
            return false; // EXIT
        }

        // when we pass here the queued message has state IN_PROGRESS

        String xmlMessage = messageRepository.findDocumentByMessageNoWithoutAccountCheck(messageMetaData.getMsgNo().toLong());
        logger.debug("Attempting to send message #" + messageMetaData.getMsgNo());
        final PeppolDocumentSender.TransmissionReceipt transmissionReceipt = documentSender.sendDocument(messageMetaData, xmlMessage);

        messageRepository.updateOutBoundMessageDeliveryDateAndUuid(
                messageMetaData.getMsgNo(),
                transmissionReceipt.getRemoteAccessPoint(),
                transmissionReceipt.getReceptionId(),
                transmissionReceipt.getTransmissionId(),
                transmissionReceipt.getDate(),
                transmissionReceipt.getReceipt()
                );

        // we got this far so delivery was ok

        return true;

    }

    /**
     * When error occurs state is updated to AOD, new entry in outbound_message_queue_error table
     * is created and error notification is sent by email
     */
    private void handleException(QueuedOutboundMessage queuedOutboundMessage, Exception e) {

        queueRepository.changeQueuedMessageState(queuedOutboundMessage.getOutboundQueueId(), OutboundMessageQueueState.AOD);

        String stackTrace = e != null ? ExceptionUtils.getStackTrace(e) : null;
        String message = e != null ? e.getMessage() : "No exception provided";

        QueuedOutboundMessageError error = new QueuedOutboundMessageError(queuedOutboundMessage.getOutboundQueueId(), "Error processing queue element", message, stackTrace);
        queueRepository.logOutboundError(error);

        logger.error( String.format("Unable to process queue item %d with messageNo %d sent; %s", queuedOutboundMessage.getOutboundQueueId().toInt(), queuedOutboundMessage.getMessageNumber().toInt(), message), e);

        MessageNumber messageNumber = queuedOutboundMessage.getMessageNumber();

        Account account = accountRepository.findAccountAsOwnerOfMessage( MessageNumber.of(messageNumber.toLong()));
        
        emailService.sendProcessingErrorNotification(account, message, queuedOutboundMessage.getMessageNumber());

    }

    private class SingleProcessingResult {
        private final boolean skipped;
        private final boolean succeeded;
        private final String errorMessage;


        private SingleProcessingResult(boolean skipped, boolean succeeded, String errorMessage) {
            this.skipped = skipped;
            this.succeeded = succeeded;
            this.errorMessage = errorMessage;
        }

        public boolean isSkipped() {
            return skipped;
        }

        public boolean isSucceeded() {
            return succeeded;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /*
     * for test purposes in protected scope
     */
    protected PeppolDocumentSender getDocumentSender() {
        return documentSender;
    }

}
