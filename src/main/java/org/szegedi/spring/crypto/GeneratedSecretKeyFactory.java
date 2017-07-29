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
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.szegedi.spring.crypto.support.ProviderBasedFactory;

/**
 * Generates a new secret key. Note that as the key is not preserved across 
 * application context restarts, whatever you might have encrypted using it will 
 * become invalid no later than when the JVM is shut down.
 * @author Attila Szegedi
 * @version $Id$
 */
public class GeneratedSecretKeyFactory extends ProviderBasedFactory
{
    private String algorithm = "AES";
    private SecureRandom secureRandom;
    private int keySize = 128;
    
    /**
     * Sets the key algorithm to use. Defaults to "AES".
     * @param algorithm the key algorithm
     */
    public void setAlgorithm(final String algorithm)
    {
        this.algorithm = algorithm;
    }
    
    /**
     * Sets the size of the keys in bits. Defaults to 128.
     * @param keySize the size of the keys.
     */
    public void setKeySize(final int keySize)
    {
        this.keySize = keySize;
    }
    
    /**
     * Sets an instance of secure random to use. If not set, a one-off instance
     * created using a private instance of {@link SecureRandomFactory} will be
     * created, with the configured security provider.
     * @param secureRandom the secure random instance to use.
     */
    public void setSecureRandom(final SecureRandom secureRandom)
    {
        this.secureRandom = secureRandom;
    }
    
    protected Object createInstance() throws Exception
    {
        if(secureRandom == null)
        {
            final SecureRandomFactory secureRandomFactory = new SecureRandomFactory();
            secureRandomFactory.setProvider(provider);
            secureRandomFactory.afterPropertiesSet();
            secureRandom = (SecureRandom)secureRandomFactory.createInstance();
        }
        KeyGenerator kg;
        if(provider == null)
        {
            kg = KeyGenerator.getInstance(algorithm);
        }
        else
        {
            kg = KeyGenerator.getInstance(algorithm, provider);
        }
        kg.init(keySize, secureRandom);
        return kg.generateKey();
    }
    
    /**
     * @return {@link SecretKey}.class
     */
    public Class getObjectType()
    {
        return SecretKey.class;
    }
}
