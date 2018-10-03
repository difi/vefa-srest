package no.sr.ringo.oxalis;

import no.difi.oxalis.api.model.TransmissionIdentifier;
import no.difi.vefa.peppol.common.model.Receipt;
import no.sr.ringo.message.MessageMetaData;
import no.sr.ringo.message.ReceptionId;

import java.net.URI;
import java.util.Date;

/**
 * User: andy
 * Date: 2/1/12
 * Time: 12:45 PM
 */
public interface PeppolDocumentSender {

    /**
     * Sends the document referenced by the message
     *
     * @param message the Message to send
     * @param xmlMessage
     * @return a Receipt containing the UUID and the TimeStamp for the delivery
     */
    TransmissionReceipt sendDocument(MessageMetaData message, String xmlMessage) throws Exception;

    /**
     * Value Object containing
     * the Transmission Timestamp and the UUID of the transfer.
     */
    public static final class TransmissionReceipt {

        private final ReceptionId receptionId;
        private final TransmissionIdentifier transmissionId;
        private final URI remoteAccessPoint;
        private final Date date;
        private final Receipt receipt;

        public TransmissionReceipt(ReceptionId receptionId, TransmissionIdentifier transmissionId, URI remoteAccessPoint, Date date, Receipt receipt) {
            this.receptionId = receptionId;
            this.transmissionId = transmissionId;
            this.remoteAccessPoint = remoteAccessPoint;
            this.date = date;
            this.receipt = receipt;
        }

        public ReceptionId getReceptionId() {
            return receptionId;
        }

        public URI getRemoteAccessPoint() {
            return remoteAccessPoint;
        }

        public Date getDate() {
            return date;
        }

        public Receipt getReceipt() {
            return receipt;
        }

        public TransmissionIdentifier getTransmissionId() {
            return transmissionId;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("TransmissionReceipt{");
            sb.append("receptionId=").append(receptionId);
            sb.append(", transmissionId=").append(transmissionId);
            sb.append(", remoteAccessPoint='").append(remoteAccessPoint).append('\'');
            sb.append(", date=").append(date);
            sb.append(", receipt=").append(receipt);
            sb.append('}');
            return sb.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TransmissionReceipt that = (TransmissionReceipt) o;

            if (!receptionId.equals(that.receptionId)) return false;
            if (!transmissionId.equals(that.transmissionId)) return false;
            if (!remoteAccessPoint.equals(that.remoteAccessPoint)) return false;
            if (!date.equals(that.date)) return false;
            return receipt.equals(that.receipt);
        }

        @Override
        public int hashCode() {
            int result = receptionId.hashCode();
            result = 31 * result + transmissionId.hashCode();
            result = 31 * result + remoteAccessPoint.hashCode();
            result = 31 * result + date.hashCode();
            result = 31 * result + receipt.hashCode();
            return result;
        }
    }

}
