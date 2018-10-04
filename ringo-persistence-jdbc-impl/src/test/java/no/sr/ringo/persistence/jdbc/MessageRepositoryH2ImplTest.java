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

package no.sr.ringo.persistence.jdbc;

import no.difi.oxalis.test.identifier.WellKnownParticipant;
import no.difi.vefa.peppol.common.model.ParticipantIdentifier;
import no.sr.ringo.account.AccountId;
import no.sr.ringo.message.MessageMetaDataImpl;
import no.sr.ringo.message.MessageRepository;
import no.sr.ringo.message.TransmissionMetaData;
import no.sr.ringo.peppol.ChannelProtocol;
import no.sr.ringo.peppol.PeppolChannelId;
import no.sr.ringo.peppol.PeppolDocumentTypeId;
import no.sr.ringo.persistence.guice.PersistenceTestModuleFactory;
import no.sr.ringo.transport.TransferDirection;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

import javax.inject.Inject;
import javax.sql.DataSource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;

import static org.testng.Assert.*;

/**
 * @author Steinar Overbeck Cook
 * @author Thore Holmberg Johnsen
 */
@Guice(moduleFactory = PersistenceTestModuleFactory.class)
public class MessageRepositoryH2ImplTest {

    private static final String MESSAGE_COMMENT = "This is a simple JUnit test";

    @Inject
    private MessageRepository messageDbmsRepository;

    @Inject
    private DataSource dataSource;

    @Test
    public void findAccountByParticipantId() {
        MessageRepositoryH2Impl repo = (MessageRepositoryH2Impl) messageDbmsRepository;
        AccountId accountId = repo.srAccountIdForReceiver(ParticipantIdentifier.of("9908:976098897"));
        assertEquals(accountId.toInteger(), Integer.valueOf(1));
    }



    void removeFilesFor(TransmissionMetaData metaData) {

        deleteUri(metaData.getPayloadUri());
        if (metaData.getEvidenceUri() != null) {
            deleteUri(metaData.getEvidenceUri());
        }
    }

    private void deleteUri(URI p) {
        try {
            Files.deleteIfExists(Paths.get(p));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to remove " + p);
        }
    }



    private void dumpRow(ResultSet resultSet) throws SQLException {

        ResultSetMetaData metaData = resultSet.getMetaData();
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            String columnLabel = metaData.getColumnLabel(i);
            String columnName = metaData.getColumnName(i);
            String value = resultSet.getString(i);
            System.out.format("%s (%s): %s\n", columnName, columnName, value == null ? "null" : value);

        }
    }


    @Test
    public void testSaveOutboundMessage() throws ParserConfigurationException, SQLException, IOException {

        Long messageNo = messageDbmsRepository.saveOutboundMessage(sampleMessageMetaData(), sampeXmlDocumentAsInputStream());

        Connection con = dataSource.getConnection();
        PreparedStatement ps = con.prepareStatement("select * from message where msg_no=?");
        ps.setLong(1, messageNo);
        ResultSet resultSet = ps.executeQuery();
        assertTrue(resultSet.next());

        String sender = resultSet.getString("SENDER");
        assertNotNull(sender);
        assertEquals(sender, WellKnownParticipant.DIFI.getIdentifier());


        String receiver = resultSet.getString("RECEIVER");
        assertNotNull(receiver);
        assertEquals(receiver, WellKnownParticipant.DIFI_TEST.getIdentifier());

        assertNotNull(resultSet.getString("CHANNEL"));
        assertNotNull(resultSet.getString("MESSAGE_UUID"));

        String payloadUrl = resultSet.getString("PAYLOAD_URL");
        assertNotNull(payloadUrl);
        byte[] bytes = Files.readAllBytes(Paths.get(URI.create(payloadUrl)));
        String s = new String(bytes, Charset.forName("UTF-8"));
        assertTrue(s.contains(MESSAGE_COMMENT), "Oops, seems that " + payloadUrl + " does not contain the expected data!");

        // dumpRow(resultSet);
    }

    private TransmissionMetaData sampleMessageMetaData() {

        final MessageMetaDataImpl mmd = new MessageMetaDataImpl();
        mmd.setTransferDirection(TransferDirection.OUT);
        mmd.getPeppolHeader().setSender(WellKnownParticipant.DIFI);
        mmd.getPeppolHeader().setReceiver(WellKnownParticipant.DIFI_TEST);
        mmd.getPeppolHeader().setDocumentTypeIdentifier(PeppolDocumentTypeId.EHF_INVOICE.toVefa());
        mmd.getPeppolHeader().setPeppolChannelId(new PeppolChannelId(ChannelProtocol.SREST.name()));
        mmd.setAccountId(new AccountId(1));
        return mmd;
    }

    @Test
    public void testTransformDocumentWithLSSerializer() throws Exception {
        byte[] bytes = transformDocumentWithLSSerializer(sampleXmlDocument());
        String result = new String(bytes);
        assertEquals(result, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<sr-invoice><!--This is a simple JUnit test--><person>Steinar O. Cook</person></sr-invoice>");
    }

    private Document sampleXmlDocument() throws ParserConfigurationException {

        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document sampleDocument = documentBuilder.newDocument();
        Element root = sampleDocument.createElement("sr-invoice");
        sampleDocument.appendChild(root);
        Comment comment = sampleDocument.createComment(MESSAGE_COMMENT);
        root.appendChild(comment);
        Element person = sampleDocument.createElement("person");
        root.appendChild(person);
        person.appendChild(sampleDocument.createTextNode("Steinar O. Cook"));
        return sampleDocument;
    }

    private InputStream sampeXmlDocumentAsInputStream() throws ParserConfigurationException {
        return new ByteArrayInputStream(transformDocumentWithLSSerializer(sampleXmlDocument()));
    }


    private byte[] transformDocumentWithLSSerializer(Document document) {
        DOMImplementationLS domImplementationLS = (DOMImplementationLS) document.getImplementation();
        LSSerializer serializer = domImplementationLS.createLSSerializer();
        LSOutput lsOutput = domImplementationLS.createLSOutput();
        lsOutput.setEncoding("UTF-8");
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        lsOutput.setByteStream(stream);
        serializer.write(document, lsOutput);
        return stream.toByteArray();
    }
}
