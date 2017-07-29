/*
   Copyright 2006, 2007 Attila Szegedi

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package org.szegedi.spring.crypto;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.ResourceLoader;
import org.szegedi.spring.crypto.support.ProviderBasedFactory;

/**
 * Loads a private and public key pair from a Java keystore file. This is a
 * recommended way to obtain a keypair, as it will remain valid across JVM
 * restarts.
 * @author Attila Szegedi
 * @version $Id$
 */
public class KeyStoreKeyPairFactory extends ProviderBasedFactory<KeyPair> implements ResourceLoaderAware {
    private String keyAlias = "default";
    private String keystoreType = "JKS";
    private URL keystoreUrl;
    private String keystoreResource;
    private String keyPassword;
    private ResourceLoader resourceLoader;

    /**
     * Sets the key alias (the name used to retrieve the keys from the factory).
     * Required.
     * @param keyAlias the key alias.
     */
    public void setKeyAlias(final String keyAlias) {
        this.keyAlias = keyAlias;
    }

    /**
     * Sets the resource path to the keystore file. You can use this method when
     * the keystore file is a resource managed by a resource loader. You must
     * set the resource loader as well either manually, or let the framework
     * take care of it (as it implements {@link ResourceLoaderAware}).
     * @param keystoreResource the path to the keystore resource
     */
    public void setKeystoreResource(final String keystoreResource) {
        this.keystoreResource = keystoreResource;
    }

    /**
     * Sets the type of the keystore. Defaults to "JKS".
     * @param keystoreType the type of the keystore.
     */
    public void setKeystoreType(final String keystoreType) {
        this.keystoreType = keystoreType;
    }

    /**
     * Sets the keystore URL. You must supply either a URL or a resource path
     * using {@link #setKeystoreResource(String)}.
     * @param keystoreUrl the URL to the keystore
     */
    public void setKeystoreUrl(final URL keystoreUrl) {
        this.keystoreUrl = keystoreUrl;
    }

    @Override
    public void setResourceLoader(final ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * Sets the password used to protect the private key in the keystore.
     * @param keyPassword the key password.
     */
    public void setKeyPassword(final String keyPassword) {
        this.keyPassword = keyPassword;
    }

    @Override
    protected KeyPair createInstance() throws Exception {
        final KeyStore keyStore;
        if (provider == null) {
            keyStore = KeyStore.getInstance(keystoreType);
        } else {
            keyStore = KeyStore.getInstance(keystoreType, provider);
        }
        try(final InputStream in = getKeyStoreStream()) {
            keyStore.load(in, null);
            return new KeyPair(keyStore.getCertificate(keyAlias).getPublicKey(),
                    (PrivateKey)keyStore.getKey(keyAlias, keyPassword.toCharArray()));
        }
    }

    private InputStream getKeyStoreStream() throws IOException {
        if(resourceLoader != null && keystoreResource != null) {
            return resourceLoader.getResource(keystoreResource).getInputStream();
        }
        return keystoreUrl.openStream();
    }

    /**
     * @return {@link KeyPair}.class
     */
    @Override
    public Class<?> getObjectType() {
        return KeyPair.class;
    }
}
