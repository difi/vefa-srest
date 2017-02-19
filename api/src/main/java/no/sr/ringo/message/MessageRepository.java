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

package no.sr.ringo.message;

import eu.peppol.identifier.MessageId;
import no.difi.vefa.peppol.common.model.Receipt;
import no.sr.ringo.transport.TransferDirection;
import org.w3c.dom.Document;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * Repository of messages received.
 * <p>
 * The access point will instantiate one object implementing this interface once and only once upon initialization.
 * If no custom implementations are found using the service locator, the built-in {@code SimpleMessageRepository} will be used.
 * <p>
 * Remember to use an empty constructor in your own implementation.
 * <p>
 * <p>Implementations are required to be thread safe.</p>
 *
 * @author Steinar Overbeck Cook
 * @author Thore Johnsen
 */
public interface MessageRepository {

    Long saveOutboundMessage(TransmissionMetaData messageMetaData, InputStream payloadDocument)
            throws OxalisMessagePersistenceException;

    Long saveOutboundMessage(TransmissionMetaData messageMetaData, Document payloadDocument)
            throws OxalisMessagePersistenceException;

    Long saveInboundMessage(TransmissionMetaData messageMetaData, InputStream payload)
            throws OxalisMessagePersistenceException;


    void saveOutboundTransportReceipt(Receipt transmissionEvidence, ReceptionId receptionId)
            throws OxalisMessagePersistenceException;

    TransmissionMetaData findByMessageNo(Long msgNo);

    /**
     * Find an instance of {@link MessageMetaData} by {@link TransferDirection} and {@link MessageId}, i.e. the UUID assigned when we receive a message either
     * from PEPPOL or our back end.
     * The combination of arguments {@link TransferDirection} and {@link MessageId} ensures that messages sent and received by this access point are unique.
     *
     * @param transferDirection indicates whether the message is inbound or outbound.
     * @param receptionId         the key
     * @return an instance of {@link MessageMetaData} populated with data from the repository (DBMS)
     * @throws IllegalStateException if a message with the given MessageId does not exist
     */
    Optional<? extends MessageMetaData> findByReceptionId(TransferDirection transferDirection, ReceptionId receptionId)
            throws IllegalStateException;

    List<TransmissionMetaData> findByReceptionId(ReceptionId messageId);

}
