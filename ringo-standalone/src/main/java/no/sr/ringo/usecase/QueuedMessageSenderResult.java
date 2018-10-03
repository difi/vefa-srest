package no.sr.ringo.usecase;

import no.sr.ringo.peppol.RingoUtils;
import no.sr.ringo.persistence.queue.OutboundMessageQueueId;

import java.util.Iterator;
import java.util.Map;

/**
 * User: Adam
 * Date: 2/24/12
 * Time: 11:56 AM
 */
public class QueuedMessageSenderResult {

    private final int succeeded;
    private final int skipped;

    //represents failed msgNo with error message
    private final Map<OutboundMessageQueueId, String> failed;


    public QueuedMessageSenderResult(Map<OutboundMessageQueueId, String> failed, int succeeded, int skipped) {
        this.failed = failed;
        this.succeeded = succeeded;
        this.skipped = skipped;
    }

    public String asXml() {
        StringBuilder xml = new StringBuilder();
        xml.append("<queued-messages-send-result>\n");
        xml.append("    <succeededCount>" + succeeded + "</succeededCount>\n");

        if (skipped > 0) {
            xml.append("    <skipped>" + skipped + "</skipped>\n");
        }

        if (failed != null && !failed.isEmpty()) {
            Iterator<Map.Entry<OutboundMessageQueueId, String>> entries = failed.entrySet().iterator();
            xml.append("    <failed>\n");
            while (entries.hasNext()) {
                Map.Entry<OutboundMessageQueueId, String> entry = entries.next();
                System.out.println("Key = " + entry.getKey() + ", Value = " + entry.getValue());
                xml.append("        <message>\n");
                xml.append("            <queueId>" + entry.getKey().toInt() + "</queueId>\n");
                xml.append("            <errorMessage>" + RingoUtils.encodePredefinedXmlEntities(entry.getValue()) + "</errorMessage>\n");
                xml.append("        </message>\n");
            }
            xml.append("    </failed>\n");
        }
        xml.append("</queued-messages-send-result>");
        return xml.toString();
    }
}
