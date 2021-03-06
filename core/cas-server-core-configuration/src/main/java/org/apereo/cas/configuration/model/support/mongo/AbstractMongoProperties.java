package org.apereo.cas.configuration.model.support.mongo;

/**
 * This is {@link AbstractMongoProperties}.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
public abstract class AbstractMongoProperties {
    private String clientUri = "";
    private String collection = "";
    private boolean dropCollection;

    public String getClientUri() {
        return clientUri;
    }

    public void setClientUri(final String clientUri) {
        this.clientUri = clientUri;
    }

    public String getCollection() {
        return collection;
    }

    public void setCollection(final String collection) {
        this.collection = collection;
    }

    public boolean isDropCollection() {
        return dropCollection;
    }

    public void setDropCollection(final boolean dropCollection) {
        this.dropCollection = dropCollection;
    }
}
