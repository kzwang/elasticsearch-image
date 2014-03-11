package org.elasticsearch;


public class ElasticsearchImageProcessException extends ElasticsearchException {

    public ElasticsearchImageProcessException(String msg) {
        super(msg);
    }

    public ElasticsearchImageProcessException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
