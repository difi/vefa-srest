package no.sr.ringo.parser;

/**
 * User: adam
 * Date: 3/9/13
 * Time: 2:06 PM
 */
public class ParserResult {

    public enum PROCESSING_TYPE {ALL, SINGLE}

    private final PROCESSING_TYPE processingType;
    private final Integer msgNo;

    public ParserResult(PROCESSING_TYPE processing_type,  Integer msgNo) {
        this.processingType = processing_type;
        this.msgNo = msgNo;
    }

    public PROCESSING_TYPE getProcessingType() {
        return processingType;
    }


    public Integer getQueueId() {
        return msgNo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ParserResult that = (ParserResult) o;

        if (processingType != that.processingType) return false;
        return msgNo != null ? msgNo.equals(that.msgNo) : that.msgNo == null;
    }

    @Override
    public int hashCode() {
        int result = processingType.hashCode();
        result = 31 * result + (msgNo != null ? msgNo.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ParserResult{");
        sb.append("processingType=").append(processingType);
        sb.append(", msgNo=").append(msgNo);
        sb.append('}');
        return sb.toString();
    }
}
