/*
 * Copyright (c) 2008 - 2013 10gen, Inc. <http://10gen.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mongodb.connection.impl;

import org.bson.ByteBuf;
import org.mongodb.MongoCredential;
import org.mongodb.connection.BufferProvider;
import org.mongodb.connection.Connection;
import org.mongodb.connection.ResponseBuffers;
import org.mongodb.connection.ServerAddress;

import java.util.ArrayList;
import java.util.List;

import static org.mongodb.assertions.Assertions.isTrue;
import static org.mongodb.assertions.Assertions.notNull;

class AuthenticatingConnection implements Connection {
    private final List<MongoCredential> credentialList;
    private final BufferProvider bufferProvider;
    private volatile Connection wrapped;
    private boolean authenticated;

    public AuthenticatingConnection(final Connection wrapped, final List<MongoCredential> credentialList,
                                    final BufferProvider bufferProvider) {
        this.wrapped = notNull("wrapped", wrapped);
        this.bufferProvider = notNull("bufferProvider", bufferProvider);

        notNull("credentialList", credentialList);
        this.credentialList = new ArrayList<MongoCredential>(credentialList);
        authenticateAll();
    }

    @Override
    public void close() {
        if (wrapped != null) {
            wrapped.close();
            wrapped = null;
        }
    }

    @Override
    public boolean isClosed() {
        return wrapped == null || wrapped.isClosed();
    }

    @Override
    public ServerAddress getServerAddress() {
        isTrue("open", !isClosed());
        return wrapped.getServerAddress();
    }

    @Override
    public void sendMessage(final List<ByteBuf> byteBuffers) {
        isTrue("open", !isClosed());
        wrapped.sendMessage(byteBuffers);
    }

    @Override
    public ResponseBuffers receiveMessage() {
        isTrue("open", !isClosed());
        return wrapped.receiveMessage();
    }

    @Override
    public String getId() {
        return wrapped.getId();
    }

    private void authenticateAll() {
        if (!authenticated) {
            for (MongoCredential cur : credentialList) {
                createAuthenticator(cur).authenticate();
            }
            authenticated = true;
        }
    }

    private Authenticator createAuthenticator(final MongoCredential credential) {
        switch (credential.getMechanism()) {
            case MONGODB_CR:
                return new NativeAuthenticator(credential, wrapped, bufferProvider);
            case GSSAPI:
                return new GSSAPIAuthenticator(credential, wrapped, bufferProvider);
            case PLAIN:
                return new PlainAuthenticator(credential, wrapped, bufferProvider);
            case MONGODB_X509:
                return new X509Authenticator(credential, wrapped, bufferProvider);
            default:
                throw new IllegalArgumentException("Unsupported authentication protocol: " + credential.getMechanism());
        }
    }
}
