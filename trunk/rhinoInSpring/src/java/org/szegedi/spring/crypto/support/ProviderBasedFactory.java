package org.szegedi.spring.crypto.support;

import org.springframework.beans.factory.config.AbstractFactoryBean;

public abstract class ProviderBasedFactory extends AbstractFactoryBean
{
    protected String provider;
    
    /**
     * Sets the name of the security provider. If not set, uses the default
     * security provider.
     * @param provider the name of the security provider or null for the default
     * provider.
     */
    public void setProvider(String provider)
    {
        this.provider = provider;
    }
    
}
