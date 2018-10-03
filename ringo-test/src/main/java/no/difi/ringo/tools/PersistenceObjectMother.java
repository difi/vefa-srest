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

package no.difi.ringo.tools;

import no.difi.oxalis.api.model.TransmissionIdentifier;
import no.difi.oxalis.test.identifier.WellKnownParticipant;
import no.difi.vefa.peppol.common.model.DocumentTypeIdentifier;
import no.difi.vefa.peppol.common.model.InstanceIdentifier;
import no.difi.vefa.peppol.common.model.ParticipantIdentifier;
import no.difi.vefa.peppol.common.model.ProcessIdentifier;
import no.sr.ringo.account.Account;
import no.sr.ringo.account.AccountId;
import no.sr.ringo.account.CustomerId;
import no.sr.ringo.account.UserName;
import no.sr.ringo.message.MessageMetaDataImpl;
import no.sr.ringo.message.MessageNumber;
import no.sr.ringo.message.ReceptionId;
import no.sr.ringo.peppol.ChannelProtocol;
import no.sr.ringo.peppol.PeppolChannelId;
import no.sr.ringo.transport.TransferDirection;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.Date;

/**
 * Object which shall be used to of complex objects for testing.
 *
 * @author andy
 * @author adam
 * @author thore
 */
public class PersistenceObjectMother {

    public static final String SAMPLE_SENDER = "9908:976098897";
    public static final String SAMPLE_RECEIVER = "9908:123456789";
    public static final DocumentTypeIdentifier SAMPLE_DOC_TYPE_ID = DocumentTypeIdentifier.of("urn:unittest");
    public static final ProcessIdentifier SAMPLE_PROFILE_ID = ProcessIdentifier.of("urn:unittest.profile.test");


    public static Account getTestAccount(){
        return new Account(new CustomerId(1), "AndyAccount",
                new UserName("sr"), new Date(), getTestPassword(), new AccountId(1), false, true);
    }

    public static Account getAdamsAccount() {
        return new Account(
                new CustomerId(1), "AdamAccount",
                new UserName("adam"), new Date(), getTestPassword(), new AccountId(2), false, true);
    }

    public static Account getThoresAccount() {
        return new Account(new CustomerId(1), "ThoresAccount",
                new UserName("teedjay"), new Date(), getTestPassword(), new AccountId(3), false, true);
    }

    private static String getTestPassword() {
        return "ringo";
    }

    public static ParticipantIdentifier getTestParticipantIdForSMPLookup() {
        return WellKnownParticipant.DIFI;
    }

    public static ParticipantIdentifier getTestParticipantIdForConsumerReceiver() {
        return ParticipantIdentifier.of("9999:01029400470");
    }

    public static ParticipantIdentifier getTestParticipantId() {
        return ParticipantIdentifier.of("9908:976098897");
    }

    public static ParticipantIdentifier getAdamsParticipantId() {
        return ParticipantIdentifier.of("9908:988890081");
    }

    public static final DocumentTypeIdentifier getDocumentIdForBisInvoice() {
        return DocumentTypeIdentifier.of("urn:oasis:names:specification:ubl:schema:xsd:Invoice-2::Invoice##urn:www.cenbii.eu:transaction:biitrns010:ver2.0:extended:urn:www.peppol.eu:bis:peppol4a:ver2.0::2.1");
    }


    public static final MessageMetaDataImpl sampleInboundTransmissionMetaData(){

        return sampleInboundTransmissionMetaData(null, new AccountId(1));
    }


    public static MessageMetaDataImpl sampleInboundTransmissionMetaData(MessageNumber msgNo, AccountId accountId) {
        MessageMetaDataImpl m = new MessageMetaDataImpl();

        m.setMsgNo(msgNo);
        m.setAccountId(accountId);
        m.setTransferDirection(TransferDirection.IN);
        m.setReceived(new Date());
        m.setDelivered(null);
        m.getPeppolHeader().setSender(ParticipantIdentifier.of(SAMPLE_SENDER));
        m.getPeppolHeader().setReceiver(ParticipantIdentifier.of(SAMPLE_RECEIVER));
        m.getPeppolHeader().setDocumentTypeIdentifier(SAMPLE_DOC_TYPE_ID);
        m.getPeppolHeader().setProcessIdentifier(SAMPLE_PROFILE_ID);
        m.getPeppolHeader().setPeppolChannelId(new PeppolChannelId(ChannelProtocol.AS2.name()));
        m.setReceptionId(new ReceptionId());
        m.setTransmissionId(TransmissionIdentifier.generateUUID());
        m.setSbdhInstanceIdentifier(InstanceIdentifier.generateUUID());
        m.setPayloadUri(sampleFileUri(".xml"));
        m.setEvidenceUri(sampleFileUri(".receipt.dat"));

        return m;    }

    static URI sampleFileUri(String suffix) {
        final URI samplePayload;
        try {
            samplePayload = Files.createTempFile("sample", suffix).toUri();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to of sample file; " + e.getMessage(), e);
        }
        return samplePayload;
    }
}
