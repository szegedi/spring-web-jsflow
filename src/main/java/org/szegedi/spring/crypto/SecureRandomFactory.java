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

import java.security.SecureRandom;
import org.szegedi.spring.crypto.support.ProviderBasedFactory;

/**
 * Generates a {@link SecureRandom} instance using a specified algorithm and
 * security provider.
 * @author Attila Szegedi
 */
public class SecureRandomFactory extends ProviderBasedFactory<SecureRandom> {
    private String algorithm = "SHA1PRNG";

    /**
     * Sets the pseudorandom algorithm to use. Defaults to "SHA1PRNG".
     * @param algorithm the pseudorandom algorithm to use.
     */
    public void setAlgorithm(final String algorithm) {
        this.algorithm = algorithm;
    }

    @Override
    protected SecureRandom createInstance() throws Exception {
        if(provider == null) {
            return SecureRandom.getInstance(algorithm);
        }
        return SecureRandom.getInstance(algorithm, provider);
    }

    /**
     * @return {@link SecureRandom}.class
     */
    @Override
    public Class<?> getObjectType() {
        return SecureRandom.class;
    }
}
