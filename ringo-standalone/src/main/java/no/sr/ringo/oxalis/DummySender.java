package no.sr.ringo.oxalis;

import no.difi.oxalis.api.model.TransmissionIdentifier;
import no.difi.vefa.peppol.common.model.Receipt;
import no.sr.ringo.message.MessageMetaData;
import no.sr.ringo.message.PeppolMessageRepository;
import no.sr.ringo.message.ReceptionId;

import javax.inject.Inject;
import java.util.Date;

/**
 * Sender used when operating in Test mode.
 * User: andy
 * Date: 2/3/12
 * Time: 11:59 AM
 */
public class DummySender implements PeppolDocumentSender {

    private final PeppolMessageRepository messageRepository;
    private final TransmissionReceipt transmissionReceipt;

    @Inject
    public DummySender(PeppolMessageRepository messageRepository) {
        this.messageRepository = messageRepository;
        this.transmissionReceipt = null;
    }

    /*
    DummySender(PeppolMessageRepository messageRepository, TransmissionReceipt transmissionReceipt) {
        this.messageRepository = messageRepository;
        this.transmissionReceipt = transmissionReceipt;
    }
    */

    /**
     * Sends the document referenced by the message
     *
     * @param message the Message to send
     * @return a Recipient containing the UUID and the TimeStamp for the delivery
     */
    @Override
    public TransmissionReceipt sendDocument(MessageMetaData message, String xmlMessage) {

        //returns the receipt specified in the constructor (so we can test) if not null
        //otherwise generate a new Transmission receipt
        TransmissionReceipt result = transmissionReceipt == null ? new TransmissionReceipt(new ReceptionId(), TransmissionIdentifier.of("dummy"),
                null, new Date(), Receipt.of("native bytes".getBytes())) : transmissionReceipt;

        //if sending to yourself
        if (messageRepository.isSenderAndReceiverAccountTheSame(message.getMsgNo().toLong())) {
            //duplicate out to in message without msg_id and delivered
            messageRepository.copyOutboundMessageToInbound(message.getMsgNo().toLong(), result.getReceptionId());
        }

        return result;
    }

}
