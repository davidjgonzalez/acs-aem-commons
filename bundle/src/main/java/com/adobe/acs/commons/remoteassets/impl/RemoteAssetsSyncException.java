package com.adobe.acs.commons.remoteassets.impl;

public class RemoteAssetsSyncException extends Throwable {
    public RemoteAssetsSyncException(final String message) {
        super(message);
    }

    public RemoteAssetsSyncException(final Exception ex) {
        super(ex);
    }
}
