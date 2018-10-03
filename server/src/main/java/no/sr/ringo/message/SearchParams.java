package no.sr.ringo.message;

import no.difi.vefa.peppol.common.model.ParticipantIdentifier;
import no.sr.ringo.resource.InvalidUserInputWebException;
import no.sr.ringo.transport.TransferDirection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.UriBuilder;

/**
 * Created by IntelliJ IDEA.
 * User: adam
 * Date: 1/22/12
 * Time: 2:47 PM
 * Simple DTO representing resource search parameters
 */
public class SearchParams implements MessageSearchParams {
    static final Logger log = LoggerFactory.getLogger(SearchParams.class);
    // not using dateFormat as it's not as restrictive as regex, expected date format is "yyyy-MM-dd"
    protected static final String DATE_FORMAT_REGEX = "[0-9]{4,4}-[0-2][0-9]-[0-3][0-9]";

    private final ParticipantIdentifier receiver;
    private final ParticipantIdentifier sender;
    private final TransferDirection direction;
    //mapped to received in database
    private String sent;
    private DateCondition dateCondition;

    /* describes page index, defaults to 1 if not specified*/
    private final Integer pageIndex;

    /*
     * @param sent contains both search condition and the date itself, e.g. '<=2012-01-01', and will be parsed
     * into two separate fields
     */
    public SearchParams(String direction, String sender, String receiver, String sent, String index) {

        this.sender = parseParticipantId(sender);
        this.receiver = parseParticipantId(receiver);
        this.direction = parseDirection(direction);
        this.pageIndex = parseIndex(index);
        parseDate(sent);

    }

    /**
     * Getters and setters
     */

    public String getSent() {
        return sent;
    }


    public DateCondition getDateCondition() {
        return dateCondition;
    }

    public ParticipantIdentifier getReceiver() {
        return receiver;
    }

    public ParticipantIdentifier getSender() {
        return sender;
    }

    public TransferDirection getDirection() {
        return direction;
    }

    public Integer getPageIndex() {
        return pageIndex;
    }

    /**
     * Appends all query params appart from the page index to the uriBuilder provided
     * @param uriBuilder
     */
    public void appendTo(UriBuilder uriBuilder) {
        if(this.sender != null) {
            uriBuilder.queryParam("sender", sender.getIdentifier());
        }
        if(this.receiver !=null) {
            uriBuilder.queryParam("receiver", receiver.getIdentifier());
        }
        if(this.sent !=null) {
            uriBuilder.queryParam("sent", String.format("'%s%s'",dateCondition.getValue(), sent));
        }
        if (this.direction != null) {
            uriBuilder.queryParam("direction", direction.name());
        }
    }


    /**
     * Parses string value to ParticipantIt if it's not empty and not contains quotes only
     *
     * @param participantId
     * @return
     */
    private ParticipantIdentifier parseParticipantId(String participantId) {
        String participantIdWithoutQuotes = removeQuotes(participantId);
        if (participantIdWithoutQuotes == null || participantIdWithoutQuotes.trim().length() == 0) {
            return null;
        } else {
            return ParticipantIdentifier.of(participantIdWithoutQuotes);
        }
    }

    /**
     * Removes qoutes from given string
     *
     * @param value
     * @return null if value is null | empty | contains quotes only
     */
    private String removeQuotes(String value) {
        if (value == null || value.trim().length() == 0) {
            return null;
        }
        String withoutQuotes = value.replace("'", "").replace("%22", "");
        return withoutQuotes == null || withoutQuotes.trim().length() == 0 ? null : withoutQuotes;

    }

    /**
     * Helper method which parses sent parameter into date condition and the date itself
     * e.g. '<=2012-01-01' will be parsed to DateCondition.LESS_EQUAL and the remaining part to date
     *
     * @param sent
     */
    private void parseDate(String sent) {
        String value = removeQuotes(sent);

        if (value == null) {
            return;
        }

        try {

            int argLength = determinArgLength(value);
            parseDateCondition(value, argLength);
            parseDateString(value, argLength);

        } catch (Exception e) {
            log.debug("Invalid date param ", e);
            throw new InvalidUserInputWebException(String.format("Invalid date condition for parameter %s", value));
        }
    }

    private int determinArgLength(String value) {
        int argLength;
        if (value.charAt(1) == '=') {
            argLength=2;
        } else if (value.charAt(0) == '<' || value.charAt(0) == '=' || value.charAt(0) == '>') {
            argLength=1;
        } else {
            throw new IllegalArgumentException();
        }
        return argLength;
    }

    private void parseDateString(String value,int length) {
        String dateString = value.substring(length);
        validateDateFormat(dateString);
        this.sent = dateString;
    }

    private void parseDateCondition(String value,int index) {
        this.dateCondition = DateCondition.fromString(value.substring(0, index));
    }


    /**
     * Makes sure that date is in the correct format, i.e. yyyy-MM-dd. Using regex because dateFormatter is not enough
     *
     * @param dateString
     */
    private void validateDateFormat(String dateString) {
        if (!dateString.matches(DATE_FORMAT_REGEX)) {
            throw new InvalidUserInputWebException(String.format("Not a valid date: %s", dateString));
        }
    }

    private TransferDirection parseDirection(String direction) {
        if (direction != null) {
            try {
                return TransferDirection.valueOf(direction.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new InvalidUserInputWebException(String.format("Wrong direction value '%s'. It must be either IN or OUT", direction));
            }
        } else {
            return null;
        }
    }

    /**
     * Parses index from String
     * @param index
     * @return index page or 1 if none specified
     */
    private Integer parseIndex(String index) {
        if (index != null && index.trim().length() > 0) {
            try {
                Integer result = Integer.parseInt(index);
                if (result < 1) {
                    throw new InvalidUserInputWebException(String.format("Index must be a positive number: %s", index));
                }
                return result;
            }catch (NumberFormatException e) {
                throw new InvalidUserInputWebException(String.format("Index must be a number: %s", index));
            }
        } else {
            return 1;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SearchParams that = (SearchParams) o;

        if (dateCondition != that.dateCondition) return false;
        if (direction != that.direction) return false;
        if (pageIndex != null ? !pageIndex.equals(that.pageIndex) : that.pageIndex != null) return false;
        if (receiver != null ? !receiver.equals(that.receiver) : that.receiver != null) return false;
        if (sender != null ? !sender.equals(that.sender) : that.sender != null) return false;
        if (sent != null ? !sent.equals(that.sent) : that.sent != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = receiver != null ? receiver.hashCode() : 0;
        result = 31 * result + (sender != null ? sender.hashCode() : 0);
        result = 31 * result + (direction != null ? direction.hashCode() : 0);
        result = 31 * result + (sent != null ? sent.hashCode() : 0);
        result = 31 * result + (dateCondition != null ? dateCondition.hashCode() : 0);
        result = 31 * result + (pageIndex != null ? pageIndex.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "SearchParams{" +
                "receiver=" + receiver +
                ", sender=" + sender +
                ", direction=" + direction +
                ", sent='" + sent + '\'' +
                ", dateCondition=" + dateCondition +
                ", pageIndex=" + pageIndex +
                '}';
    }
}
