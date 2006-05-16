package org.szegedi.spring.beans.factory;

import java.util.Map;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

public class BeanFactoryUtilsEx
{

    public static Object beanOfTypeIncludingAncestors(ListableBeanFactory lbf, Class type)
    {
        Map map = org.springframework.beans.factory.BeanFactoryUtils.beansOfTypeIncludingAncestors(lbf, type);
        switch(map.size())
        {
            case 0:
            {
                return null;
            }
            case 1:
            {
                return map.values().iterator().next();
            }
            default:
            {
                throw new NoSuchBeanDefinitionException(
                        "More than one bean of type " + type.getName()  + 
                        " found: " + map.keySet()); 
            }
        }
    }

}
