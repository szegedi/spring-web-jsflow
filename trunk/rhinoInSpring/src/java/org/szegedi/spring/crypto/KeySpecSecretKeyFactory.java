package org.szegedi.spring.crypto;

import java.security.spec.KeySpec;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;

import org.szegedi.spring.crypto.support.ProviderBasedFactory;

/**
 * Generates a secret key from an implementation-independent secret key 
 * specification. Tipically, you can construct such a specification in memory,
 * or load it from a serialized file, or synthesize it from a password. Equal
 * specifications result in identical keys, thus you can use it to construct a
 * key valid accross JVM restarts.
 * @author Attila Szegedi
 * @version $Id: $
 */
public class KeySpecSecretKeyFactory extends ProviderBasedFactory
{
    private String algorithm;
    private KeySpec keySpec;
    
    /**
     * Sets the algorithm for the secret key. Required.
     * @param algorithm the secret key algorithm
     */
    public void setAlgorithm(String algorithm)
    {
        this.algorithm = algorithm;
    }
    
    /**
     * Sets the key specification that defines the key in an implementation
     * independent manner.
     * @param keySpec the key specification.
     */
    public void setKeySpec(KeySpec keySpec)
    {
        this.keySpec = keySpec;
    }
    
    protected Object createInstance() throws Exception
    {
        SecretKeyFactory skf;
        if(provider == null)
        {
            skf = SecretKeyFactory.getInstance(algorithm);
        }
        else
        {
            skf = SecretKeyFactory.getInstance(algorithm, provider);
        }
        return skf.generateSecret(keySpec);
    }
    
    /**
     * @return {@link SecretKey}.class
     */
    public Class getObjectType()
    {
        return SecretKey.class;
    }
}
