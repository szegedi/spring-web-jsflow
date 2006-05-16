package org.szegedi.spring.crypto;

import java.security.SecureRandom;

import org.szegedi.spring.crypto.support.ProviderBasedFactory;

/**
 * Generates a {@link SecureRandom} instance using a specified algorithm and
 * security provider.
 * @author Attila Szegedi
 * @version $Id: $
 */
public class SecureRandomFactory extends ProviderBasedFactory
{
    private String algorithm = "SHA1PRNG";
    
    /**
     * Sets the pseudorandom algorithm to use. Defaults to "SHA1PRNG".
     * @param algorithm the pseudorandom algorithm to use.
     */
    public void setAlgorithm(String algorithm)
    {
        this.algorithm = algorithm;
    }
    
    protected Object createInstance() throws Exception
    {
        if(provider == null)
        {
            return SecureRandom.getInstance(algorithm);
        }
        else
        {
            return SecureRandom.getInstance(algorithm, provider);
        }
    }
    
    /**
     * @return {@link SecureRandom}.class
     */
    public Class getObjectType()
    {
        return SecureRandom.class;
    }
}
