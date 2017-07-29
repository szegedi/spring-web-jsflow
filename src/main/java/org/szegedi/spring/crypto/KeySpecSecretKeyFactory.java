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

import java.security.spec.KeySpec;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import org.szegedi.spring.crypto.support.ProviderBasedFactory;

/**
 * Generates a secret key from an implementation-independent secret key
 * specification. Typically, you can construct such a specification in memory,
 * or load it from a serialized file, or synthesize it from a password. Equal
 * specifications result in identical keys, thus you can use it to construct a
 * key valid across JVM restarts.
 * @author Attila Szegedi
 */
public class KeySpecSecretKeyFactory extends ProviderBasedFactory<SecretKey> {
    private String algorithm;
    private KeySpec keySpec;

    /**
     * Sets the algorithm for the secret key. Required.
     * @param algorithm the secret key algorithm
     */
    public void setAlgorithm(final String algorithm) {
        this.algorithm = algorithm;
    }

    /**
     * Sets the key specification that defines the key in an implementation
     * independent manner.
     * @param keySpec the key specification.
     */
    public void setKeySpec(final KeySpec keySpec) {
        this.keySpec = keySpec;
    }

    @Override
    protected SecretKey createInstance() throws Exception {
        final SecretKeyFactory skf;
        if(provider == null) {
            skf = SecretKeyFactory.getInstance(algorithm);
        } else {
            skf = SecretKeyFactory.getInstance(algorithm, provider);
        }
        return skf.generateSecret(keySpec);
    }

    /**
     * @return {@link SecretKey}.class
     */
    @Override
    public Class<?> getObjectType() {
        return SecretKey.class;
    }
}
